/* Copyright 2014–2019 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import static controllers.nwbib.Application.CONFIG;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;

import play.Logger;
import play.Play;
import play.cache.Cache;
import play.libs.Json;

/**
 * NWBib classification and spatial classification data access via Elasticsearch
 *
 * @author Fabian Steeg (fsteeg)
 */
public class Classification {

	private static final String WIKIDATA = "http://www.wikidata.org/entity/";
	private static final String NRW = WIKIDATA + "Q1198";
	private static final String NWBIB_SUBJECTS = "https://nwbib.de/subjects#";
	private static final String NWBIB_SPATIAL = "https://nwbib.de/spatial#";
	private static final String INDEX = "nwbib";

	/**
	 * NWBib classification types.
	 */
	public enum Type {
		/** NWBib subject type */
		NWBIB("json-ld-nwbib", "Sachsystematik"), //
		/** NWBib spatial type */
		SPATIAL("json-ld-nwbib-spatial", "Raumsystematik");

		String elasticsearchType;
		String queryParameter;

		private Type(String elasticsearchType, String queryParameter) {
			this.elasticsearchType = elasticsearchType;
			this.queryParameter = queryParameter;
		}

		/**
		 * @param t The query parameter string for the classification type
		 * @return The type objects for the given string, or null
		 */
		public static Type from(String t) {
			for (Type indexType : Type.values())
				if (indexType.queryParameter.equalsIgnoreCase(t))
					return indexType;
			return null;
		}

		/**
		 * @return A pair of the list of top-level items and the hierarchy of items
		 */
		public Pair<List<JsonNode>, Map<String, List<JsonNode>>> buildHierarchy() {
			List<JsonNode> topClasses = new ArrayList<>();
			Map<String, List<JsonNode>> subClasses = new HashMap<>();
			for (SearchHit hit : classificationData().getHits()) {
				JsonNode json = Json.toJson(hit.getSource());
				JsonNode broader = json.findValue(Property.BROADER.value);
				if (broader == null) {
					topClasses.addAll(valueAndLabelWithNotation(hit, json));
				} else
					addAsSubClass(subClasses, hit, json,
							toNwbibNamespace(broader.findValue("@id").asText()));
			}
			if (this == SPATIAL && (CONFIG.getBoolean("index.nwbibspatial.enrich")
					|| Play.isTest())) { /* SpatialToSkos uses Play test server */
				addFromWikidata(subClasses);
			}
			Collections.sort(topClasses, comparator);
			return Pair.of(topClasses, subClasses);
		}

		private static void addFromWikidata(
				Map<String, List<JsonNode>> subClasses) {
			JsonNode wikidataJson = WikidataLocations.load();
			Pair<List<JsonNode>, Map<String, List<JsonNode>>> topAndSub =
					Classification.buildHierarchyWikidata(wikidataJson);
			String n05 = NWBIB_SPATIAL + "N05";
			List<JsonNode> n05Sub = new ArrayList<>();
			n05Sub.addAll(topAndSub.getLeft());
			subClasses.put(n05, n05Sub);
			Map<String, List<JsonNode>> right = topAndSub.getRight();
			for (Entry<String, List<JsonNode>> e : right.entrySet()) {
				String key = e.getKey();
				List<JsonNode> list = subClasses.containsKey(key) ? subClasses.get(key)
						: new ArrayList<>();
				List<JsonNode> additionalSubclasses = e.getValue();
				for (JsonNode candidate : additionalSubclasses) {
					// Don't replace an existing entry with a Wikidata entry, see
					// https://github.com/hbz/nwbib/pull/546#issuecomment-618610540
					if (list.stream().noneMatch(existing -> existing.get("value")
							.textValue().equals(candidate.get("value").textValue())))
						list.add(candidate);
				}
				if (!additionalSubclasses.stream()
						.anyMatch(n -> subClasses.values().stream().flatMap(List::stream)
								.collect(Collectors.toList()).contains(n))) {
					subClasses.put(key, list);
				}
			}
		}

		/**
		 * @return A sorted register of items
		 */
		public JsonNode buildRegister() {
			final List<JsonNode> result = ids(classificationData()).stream()
					.sorted(comparator).collect(Collectors.toList());
			return Json.toJson(result);
		}

		private SearchResponse classificationData() {
			int maxSize = 10000; // default max_result_window
			MatchAllQueryBuilder matchAll = QueryBuilders.matchAllQuery();
			SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
					.setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setQuery(matchAll)
					.setTypes(elasticsearchType).setFrom(0).setSize(maxSize);
			return requestBuilder.execute().actionGet();
		}
	}

	private enum Property {
		LABEL("http://www.w3.org/2004/02/skos/core#prefLabel"), //
		BROADER("http://www.w3.org/2004/02/skos/core#broader"), //
		NOTATION("http://www.w3.org/2004/02/skos/core#notation");

		String value;

		private Property(String value) {
			this.value = value;
		}
	}

	private enum Label {
		WITH_NOTATION, PLAIN
	}

	private static Client client;
	private static Node node;

	/** Compare German strings */
	public static Comparator<JsonNode> comparator =
			(JsonNode o1, JsonNode o2) -> Collator.getInstance(Locale.GERMAN)
					.compare(labelText(o1), labelText(o2));

	private Classification() {
		/* Use via static functions, no instantiation. */
	}

	/**
	 * @param turtleUrl The URL of the RDF in TURTLE format
	 * @return The input, converted to JSON-LD, or null
	 */
	public static List<String> toJsonLd(final URL turtleUrl) {
		final Model model = ModelFactory.createDefaultModel();
		try {
			model.read(turtleUrl.openStream(), null, Lang.TURTLE.getName());
			StringWriter stringWriter = new StringWriter();
			RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
			Object json = JsonUtils.fromString(stringWriter.toString());
			List<Object> list = JsonLdProcessor.expand(json);
			return list.stream().map(obj -> {
				try {
					return JsonUtils.toString(obj);
				} catch (IOException e) {
					e.printStackTrace();
					return obj.toString();
				}
			}).collect(Collectors.toList());
		} catch (JsonLdError | IOException e) {
			Logger.error("Could not convert to JSON-LD", e);
		}
		return null;
	}

	/**
	 * @param q The query
	 * @param t The classification type ("Raumsystematik" or "Sachsystematik")
	 * @return A JSON representation of the classification data for q and t
	 */
	public static JsonNode ids(String q, String t) {
		QueryBuilder queryBuilder = QueryBuilders.boolQuery()
				.must(QueryBuilders.idsQuery(Type.NWBIB.elasticsearchType,
						Type.SPATIAL.elasticsearchType).ids(q));
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setQuery(queryBuilder);
		if (t.isEmpty()) {
			requestBuilder = requestBuilder.setTypes(Type.NWBIB.elasticsearchType,
					Type.SPATIAL.elasticsearchType);
		} else {
			for (Type indexType : Type.values())
				if (indexType.queryParameter.equalsIgnoreCase(t))
					requestBuilder = requestBuilder.setTypes(indexType.elasticsearchType);
		}
		SearchResponse response = requestBuilder.execute().actionGet();
		List<JsonNode> result = ids(response);
		return Json.toJson(result);
	}

	/**
	 * @param uri The NWBib classification URI
	 * @param type The ES classification type (see {@link Classification.Type})
	 * @return The label for the given classification URI
	 */
	public static String label(String uri, Type type) {
		return getValueFromIndex(uri, type.elasticsearchType,
				"http://www.w3.org/2004/02/skos/core#prefLabel");
	}

	/**
	 * @param uri The NWBib classification URI
	 * @param type The ES classification type (see {@link Classification.Type})
	 * @return The notation for the given classification URI
	 */
	public static String notation(String uri, Type type) {
		return getValueFromIndex(uri, type.elasticsearchType,
				"http://www.w3.org/2004/02/skos/core#notation");
	}

	private static String getValueFromIndex(String uri, String type,
			String field) {
		try {
			String response =
					client.prepareGet(INDEX, type, uri).get().getSourceAsString();
			if (response != null) {
				JsonNode resultNode = Json.parse(response).findValue(field);
				return resultNode != null ? resultNode.findValue("@value").textValue()
						: "";
			}
		} catch (Throwable t) {
			Logger.error(
					"Could not get classification data, index: {} type: {}, id: {} ({}: {})",
					INDEX, type, uri, t, t);
		}
		return "";
	}

	static List<JsonNode> ids(SearchResponse response) {
		List<JsonNode> result = new ArrayList<>();
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			collectLabelAndValue(hit, json, Label.PLAIN, result);
		}
		return result;
	}

	private static String labelText(JsonNode json) {
		String label = json.get("label").asText();
		if (label.contains("Stadtbezirk")) {
			List<Pair<String, String>> roman = Arrays.asList(Pair.of("I", "a"),
					Pair.of("II", "b"), Pair.of("III", "c"), Pair.of("IV", "d"),
					Pair.of("V", "e"), Pair.of("VI", "f"), Pair.of("VII", "g"),
					Pair.of("VIII", "h"), Pair.of("IX", "i"), Pair.of("X", "j"));
			Collections.sort(roman, // replace longest first
					(p1, p2) -> Integer.valueOf(p1.getLeft().length())
							.compareTo(Integer.valueOf(p2.getLeft().length())));
			for (int i = 10; i > 0; i--) { // start from end
				label = label //
						.replace(String.valueOf(i), "" + (char) ('a' + i)) // arabic 10 to 1
						.replace(roman.get(i - 1).getLeft(), roman.get(i - 1).getRight());
			}
		}
		return label;
	}

	/**
	 * @param json The Wikidata SPARQL query response
	 * @return The hierarchy for the given Wikidata JSON
	 */
	public static Pair<List<JsonNode>, Map<String, List<JsonNode>>> buildHierarchyWikidata(
			JsonNode json) {
		List<JsonNode> topClasses = new ArrayList<>();
		Map<String, List<JsonNode>> subClasses = new HashMap<>();
		Set<String> itemIds = new HashSet<>();
		json.elements().forEachRemaining(item -> {
			itemIds.add(item.get("item").get("value").textValue());
		});
		json.elements().forEachRemaining(item -> {
			String id = item.get("item").get("value").textValue();
			String label = buildLabel(item);
			String topLevelLabelPrefix = "Regierungsbezirk";
			String nwbibNamespaceId = toNwbibNamespace(id);
			String gnd =
					item.has("gnd") ? item.get("gnd").get("value").textValue() : "";
			String nwbibID = item.has("nwbibId")
					? item.get("nwbibId").get("value").textValue() : "";
			String notation = notation(item);
			long hits = Lobid.getTotalHitsNwbibClassification(nwbibNamespaceId);
			JsonNode itemJson =
					Json.toJson(ImmutableMap.builder().put("value", nwbibNamespaceId)
							.put("label", label).put("gnd", gnd).put("hits", hits)
							.put("notation", notation).put("nwbibId", nwbibID).build());
			if (label.startsWith(topLevelLabelPrefix)) {
				topClasses.add(itemJson);
			} else if (item.has("partOf")) {
				String broaderId =
						WIKIDATA + item.get("partOf").get("value").textValue();
				if (id.equals(NRW)) {
					topClasses.add(Json.toJson(ImmutableMap.of("value", nwbibNamespaceId,
							"label", "Sonstige", "notation", notation)));
				}
				if ((!(broaderId.equals(NRW) && label.startsWith(topLevelLabelPrefix))
						|| (broaderId.equals(NRW)))) {
					if (!subClasses.containsKey(toNwbibNamespace(broaderId)))
						subClasses.put(toNwbibNamespace(broaderId),
								new ArrayList<JsonNode>());
					List<JsonNode> sub = subClasses.get(toNwbibNamespace(broaderId));
					sub.add(itemJson);
					Collections.sort(sub, comparator);
				}
			}
		});
		Collections.sort(topClasses, comparator);
		return Pair.of(topClasses, removeDuplicates(subClasses));
	}

	private static String buildLabel(JsonNode item) {
		String label = item.get("itemLabel").get("value").textValue();
		String isStadtbezirk = item.has("isStadtbezirk")
				? item.get("isStadtbezirk").get("value").textValue() : "";
		label = !isStadtbezirk.isEmpty() && !label.contains("Stadtbezirk")
				? label + " (Stadtbezirk)" : label;
		String dissolution = item.has("dissolutionDate")
				? item.get("dissolutionDate").get("value").textValue().split("-")[0]
				: "";
		label =
				!dissolution.isEmpty() ? label + String.format(" (bis %s)", dissolution)
						: label;
		return label;
	}

	private static String notation(JsonNode item) {
		JsonNode notationFromSkos = item.findValue(Property.NOTATION.value);
		if (notationFromSkos != null) {
			return notationFromSkos.findValue("@value").asText();
		}
		Optional<String> notationKeyWikidata =
				Stream.of("ks", "ags", "rs").filter(k -> item.has(k)).findFirst();
		return notationKeyWikidata.map(k -> item.get(k).get("value").asText())
				.orElse("");
	}

	private static Map<String, List<JsonNode>> removeDuplicates(
			Map<String, List<JsonNode>> subClasses) {
		List<String> ids = new ArrayList<>(subClasses.keySet());
		Collections.sort(ids, Comparator.comparingInt(
				s -> Integer.parseInt(s.substring((NWBIB_SPATIAL + "Q").length()))));
		for (int i = 0; i < ids.size(); i++) {
			String key = ids.get(i);
			final int j = i + 1;
			subClasses.put(key, subClasses.get(key).stream()
					.filter(unique(subClasses, ids, j)).collect(Collectors.toList()));
		}
		return subClasses;
	}

	private static Predicate<? super JsonNode> unique(
			Map<String, List<JsonNode>> subClasses, List<String> list, final int j) {
		return json -> {
			for (int i = j; i < list.size(); i++) {
				if (subClasses.get(list.get(i)).contains(json)) {
					return false;
				}
			}
			return true;
		};
	}

	private static void addAsSubClass(Map<String, List<JsonNode>> subClasses,
			SearchHit hit, JsonNode json, String broader) {
		if (!subClasses.containsKey(broader))
			subClasses.put(broader, new ArrayList<JsonNode>());
		List<JsonNode> list = subClasses.get(broader);
		list.addAll(valueAndLabelWithNotation(hit, json));
		Collections.sort(list, comparator);
	}

	private static String focus(JsonNode json) {
		String focusUri = "http://xmlns.com/foaf/0.1/focus";
		if (json.has(focusUri)) {
			return json.get(focusUri).iterator().next().get("@id").asText();
		}
		return "";
	}

	private static List<JsonNode> valueAndLabelWithNotation(SearchHit hit,
			JsonNode json) {
		List<JsonNode> result = new ArrayList<>();
		collectLabelAndValue(hit, json, Label.WITH_NOTATION, result);
		return result;
	}

	private static void collectLabelAndValue(SearchHit hit, JsonNode json,
			Label style, List<JsonNode> result) {
		final JsonNode label = json.findValue(Property.LABEL.value);
		if (label != null) {
			String id = toNwbibNamespace(hit.getId());
			String notation = notation(json);
			ImmutableMap<String, ?> map = ImmutableMap.of(//
					"value", id, //
					"label",
					(style == Label.PLAIN || notation.isEmpty() ? ""
							: "<span class='notation'>" + notation + "</span>" + " ")
							+ label.findValue("@value").asText(), //
					"hits", Lobid.getTotalHitsNwbibClassification(id), //
					"notation", notation, //
					"focus", focus(json));
			result.add(Json.toJson(map));
		}
	}

	static String toNwbibNamespace(String id) {
		return id //
				.replace("http://purl.org/lobid/nwbib-spatial#n", NWBIB_SPATIAL + "N")
				.replace("http://purl.org/lobid/nwbib#s", NWBIB_SUBJECTS + "N")
				.replace(WIKIDATA, NWBIB_SPATIAL);
	}

	/**
	 * @param uri The full URI
	 * @return A short, human readable representation of the URI
	 */
	public static String shortId(String uri) {
		return uri.contains("#") ? uri.split("#")[1].substring(1) : uri;
	}

	/** Start up the embedded Elasticsearch classification index. */
	public static void indexStartup() {
		Settings clientSettings = ImmutableSettings.settingsBuilder()
				.put("path.home", new File(".").getAbsolutePath())
				.put("http.port",
						play.Play.application().isTest() ? "8855"
								: CONFIG.getString("index.es.port.http"))
				.put("transport.tcp.port", play.Play.application().isTest() ? "8856"
						: CONFIG.getString("index.es.port.tcp"))
				.build();
		node =
				NodeBuilder.nodeBuilder().settings(clientSettings).local(true).node();
		client = node.client();
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		if (!client.admin().indices().prepareExists(INDEX).execute().actionGet()
				.isExists()) {
			indexData(CONFIG.getString("index.data.nwbibsubject"), Type.NWBIB);
			indexData(CONFIG.getString("index.data.nwbibspatial"), Type.SPATIAL);
			client.admin().indices().refresh(new RefreshRequest()).actionGet();
		}
	}

	private static void indexData(String dataUrl, Type type) {
		Logger.debug("Indexing from dataUrl: {}, type: {}, index: {}, client {}",
				dataUrl, type.elasticsearchType, INDEX, client);
		final BulkRequestBuilder bulkRequest = client.prepareBulk();
		try {
			List<String> jsonLd = toJsonLd(new URL(dataUrl));
			for (String concept : jsonLd) {
				String id = Json.parse(concept).findValue("@id").textValue();
				IndexRequestBuilder indexRequest = client
						.prepareIndex(INDEX, type.elasticsearchType, id).setSource(concept);
				bulkRequest.add(indexRequest);
			}
		} catch (MalformedURLException e) {
			Logger.error("Could not index data", e);
		}
		BulkResponse response = bulkRequest.execute().actionGet();
		if (response.hasFailures()) {
			Logger.info("Indexing response: {}", response.buildFailureMessage());
		}
	}

	/** Shut down the embedded Elasticsearch classification index. */
	public static void indexShutdown() {
		node.close();
	}

	/**
	 * @param uri The nwbib or nwbibspatial URI
	 * @return The list of path segments to the given URI in its classification,
	 *         e.g. for URI https://nwbib.de/subjects#N582060:
	 *         [https://nwbib.de/subjects#N5, https://nwbib.de/subjects#N580000,
	 *         https://nwbib.de/subjects#N582000,
	 *         https://nwbib.de/subjects#N582060]
	 */
	public static List<String> pathTo(String uri) {
		Type type = uri.contains("spatial") || uri.contains("wikidata")
				? Type.SPATIAL : Type.NWBIB;
		Map<String, List<String>> candidates = Cache.getOrElse(type.toString(),
				() -> generateAllPaths(type.buildHierarchy()), Application.ONE_DAY);
		return candidates.containsKey(uri) ? candidates.get(uri)
				: Arrays.asList(uri);
	}

	private static Map<String, List<String>> generateAllPaths(
			Pair<List<JsonNode>, Map<String, List<JsonNode>>> all) {
		HashMap<String, List<String>> result = new HashMap<>();
		List<JsonNode> top = all.getLeft();
		Map<String, List<JsonNode>> subs = all.getRight();
		for (JsonNode topNode : top) {
			String topId = toNwbibNamespace(topNode.get("value").asText());
			ArrayList<String> subResult = new ArrayList<>();
			ul(subs.get(topId), subs, subResult, result, topId);
		}
		return result;
	}

	private static Map<String, List<String>> ul(List<JsonNode> classes,
			Map<String, List<JsonNode>> subs, List<String> subResult,
			Map<String, List<String>> fullResult, String current) {
		if (!subResult.contains(current))
			subResult.add(current);
		for (JsonNode n : classes) {
			String value = n.get("value").asText();
			ArrayList<String> newSub = new ArrayList<>(subResult);
			newSub.add(value);
			if (subs != null && subs.get(value) != null) {
				ul(subs.get(value), subs, newSub, fullResult, value);
			}
			addResult(newSub, fullResult);
		}
		return fullResult;
	}

	private static void addResult(List<String> subResult,
			Map<String, List<String>> fullResult) {
		String last = subResult.get(subResult.size() - 1);
		if (subResult.size() > 1 && !fullResult.containsKey(last)) {
			fullResult.put(last, new ArrayList<>(subResult));
		}
	}

}
