/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.collect.Lists;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;

import controllers.nwbib.Classification.Type;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.Http;

/**
 * Access Lobid title data.
 *
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Lobid {

	private static final String GND_PREFIX = "https://d-nb.info/gnd/";

	/** Timeout for API calls in milliseconds. */
	public static final int API_TIMEOUT = 50000;

	private static Map<String, Long> AGGREGATION_COUNT = new HashMap<>();

	/**
	 * @param id The resource ID
	 * @return The resource JSON content
	 */
	public static JsonNode getResource(String id) {
		String url =
				String.format(Application.CONFIG.getString("indexUrlFormat"), id);
		JsonNode response = cachedJsonCall(url);
		return response;
	}

	/**
	 * @param url The URL to call
	 * @return A JSON response from the URL, or an empty JSON object
	 */
	public static JsonNode cachedJsonCall(String url) {
		String cacheKey = String.format("json.%s", url);
		JsonNode json = (JsonNode) Cache.get(cacheKey);
		if (json != null) {
			return json;
		}
		Logger.debug("Not cached, GET: {}", url);
		Promise<JsonNode> promise = WS.url(url).get()
				.map(response -> response.getStatus() == Http.Status.OK
						? response.asJson()
						: Json.newObject());
		promise.onRedeem(jsonResponse -> {
			Cache.set(cacheKey, jsonResponse, Application.ONE_DAY);
		});
		return promise.get(Lobid.API_TIMEOUT);
	}

	static Long getTotalResults(JsonNode json) {
		return json.findValue("totalItems").asLong();
	}

	static WSRequest request(final String q, final String person,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, String owner, String t, String sort, String location,
			String word, String corporation, String raw) {
		WSRequest requestHolder = WS.url(Application.CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("format", "json")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "")//
				.setQueryParameter("sort", sort)//
				.setQueryParameter("filter",
						Application.CONFIG.getString("nwbib.filter"))
				.setQueryParameter("location", locationPolygon(location));

		if (!raw.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("q",
					q + (q.isEmpty() ? "" : " AND ") + raw);
		requestHolder = setupWordParameter(q, nwbibspatial, word, requestHolder);
		if (!name.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("name", name);
		if (!nwbibsubject.trim().isEmpty() && !subject.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("subject",
					subjectAndNwbibSubject(subject, nwbibsubject));
		if (!subject.trim().isEmpty() && nwbibsubject.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("subject", subject);
		if (!id.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("id", id);
		if (!publisher.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("publisher", publisher);
		if (!issued.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("issued", issued);
		if (!medium.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("medium", medium);
		if (!nwbibsubject.trim().isEmpty() && subject.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("subject", nwbibsubject);
		if (!owner.isEmpty())
			requestHolder = requestHolder.setQueryParameter("owner", owner);
		if (!t.isEmpty())
			requestHolder = requestHolder.setQueryParameter("t", t);
		if (!person.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("nested",
					nestedContribution(person, "Person"));
		if (!corporation.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("nested",
					nestedContribution(corporation, "CorporateBody"));

		if (requestHolder.getQueryParameters().get("q") == null
				&& requestHolder.getQueryParameters().get("word") == null) {
			requestHolder.setQueryParameter("word", "*");
		}
		Logger.debug("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder;
	}

	private static WSRequest setupWordParameter(final String q,
			final String nwbibspatial, String word, WSRequest requestHolder) {
		if (!q.trim().isEmpty() && nwbibspatial.isEmpty())
			return requestHolder.setQueryParameter("word", preprocess(q));
		else if (!q.trim().isEmpty() && !nwbibspatial.isEmpty())
			return requestHolder.setQueryParameter("word",
					preprocess(q) + " AND " + setUpNwbibspatial(nwbibspatial));
		else if (!word.isEmpty() && nwbibspatial.isEmpty())
			return requestHolder.setQueryParameter("word", preprocess(word));
		else if (!word.isEmpty() && !nwbibspatial.trim().isEmpty()) {
			return requestHolder.setQueryParameter("word",
					preprocess(word) + " AND " + setUpNwbibspatial(nwbibspatial));
		} else if (!nwbibspatial.trim().isEmpty())
			return requestHolder.setQueryParameter("word",
					setUpNwbibspatial(nwbibspatial));
		return requestHolder;
	}

	/**
	 * @param uri The URI to test
	 * @return True, if the given URI is a Wikidata URI
	 */
	public static boolean isWikidata(final String uri) {
		return uri.contains("wikidata")
				|| uri.startsWith("https://nwbib.de/spatial#Q");
	}

	private static String nestedContribution(final String person, String type) {
		String p = person.contains(" AND ") ? person : person.replace(" ", " AND ");
		p = p.matches("[\\d\\-X]+") ? GND_PREFIX + p : p;
		p = p.startsWith("http") ? "\"" + p + "\"" : p;
		return String.format("contribution:(contribution.agent.label:(%s) "
				+ "OR contribution.agent.altLabel:(%s) "
				+ "OR contribution.agent.id:(%s)) "//
				+ "AND contribution.agent.type:(%s)", p, p, p, type);
	}

	static WSRequest topicRequest(final String q, int from, int size) {
		WSRequest request = // @formatter:off
				WS.url(Application.CONFIG.getString("nwbib.api"))
						.setHeader("Accept", "application/json")
						.setQueryParameter("format", "json")
						.setQueryParameter("from", "" + from)
						.setQueryParameter("size", "" + size)
						.setQueryParameter("subject", q)
						.setQueryParameter("filter",
								Application.CONFIG.getString("nwbib.filter"))
						.setQueryParameter("aggregations", "topic");
		//@formatter:on
		Logger.debug("Request URL {}, query params {} ", request.getUrl(),
				request.getQueryParameters());
		return request;
	}

	/**
	 * @param set The data set, uses the config file set if empty
	 * @return The full number of hits, ie. the size of the given data set
	 */
	public static Promise<Long> getTotalHits(String set) {
		String cacheKey = String.format("totalHits.%s", set);
		final Long cachedResult = (Long) Cache.get(cacheKey);
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		WSRequest requestHolder = request("", "", "", "", "", "", "", "", "", "", 0,
				0, "", "", "", "", "", "", "");
		return requestHolder.get().map((WSResponse response) -> {
			JsonNode json = response.asJson();
			Long total = getTotalResults(json);
			Cache.set(cacheKey, total, Application.ONE_HOUR);
			return total;
		});
	}

	/**
	 * @param value The nwbib classification URI
	 * @return The number of hits for the given value in an nwbib query
	 */
	public static long getTotalHitsNwbibClassification(String value) {
		if (AGGREGATION_COUNT.isEmpty()) {
			initAggregation("spatial.id");
			initAggregation("subject.id");
		}
		return (AGGREGATION_COUNT.containsKey(value) || isWikidata(value))
				? AGGREGATION_COUNT.getOrDefault(value, 0L) : lobidRequest(value);
	}

	private static Long lobidRequest(String value) {
		return request("", "", "", "", "", "", "", "", value, "", 0, 1, "", "", "",
				"", "", "", "").get().map((WSResponse response) -> {
					return getTotalResults(response.asJson());
				}).get(Lobid.API_TIMEOUT);
	}

	private static void initAggregation(String field) {
		getFacets("", "", "", "", "", "", "", "", "", "", "", field, "", "", "", "",
				"").get(Lobid.API_TIMEOUT).get("aggregation").get(field).elements()
						.forEachRemaining((JsonNode node) -> {
							AGGREGATION_COUNT.put(node.get("key").textValue(),
									node.get("doc_count").longValue());
						});
	}

	/**
	 * @param field The Elasticsearch index field
	 * @param value The value of the given field
	 * @param set The data set, uses the config file set if empty
	 * @return The number of hits for the given value in the given field
	 */
	public static Promise<Long> getTotalHits(String field, String value,
			String set) {
		String f = field;
		String v = escapeUri(value);
		String cacheKey = String.format("totalHits.%s.%s.%s", f, v, set);
		final Long cachedResult = (Long) Cache.get(cacheKey);
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		String qVal = f + ":\"" + v + "\"";
		WSRequest request = WS.url(Application.CONFIG.getString("nwbib.api"))
				.setQueryParameter("format", "json").setQueryParameter("q", qVal)
				.setQueryParameter("filter",
						set.isEmpty() ? Application.CONFIG.getString("nwbib.filter") : set);
		return request.get().map((WSResponse response) -> {
			Long total = getTotalResults(response.asJson());
			Cache.set(cacheKey, total, Application.ONE_HOUR);
			return total;
		});
	}

	/**
	 * @param string The URI string to escape
	 * @return The URI string, escaped to be usable as an ES field or value
	 */
	public static String escapeUri(String string) {
		return string.replaceAll("([\\.:/#!\"])", "\\\\$1");
	}

	/**
	 * @param id A Lobid-Organisations URI or ISIL
	 * @return A human readable label for the given id
	 */
	public static String organisationLabel(String id) {
		// e.g. take DE-6 from http://lobid.org/organisations/DE-6#!
		String simpleId =
				id.replaceAll("https?://lobid.org/organisations?/(.+?)(#!)?$", "$1");
		if (simpleId.startsWith("ZDB-")) {
			return "Paket elektronischer Ressourcen: " + simpleId;
		}
		JsonNode org = cachedJsonCall(id.startsWith("http") ? id
				: Application.CONFIG.getString("orgs.api") + id);
		if (org.size() == 0) {
			if (simpleId.split("-").length == 3) {
				String superSigel = id.substring(0, id.lastIndexOf('-'));
				Logger.info("No data for: {}, trying {}", id, superSigel);
				return organisationLabel(superSigel) + ": " + simpleId;
			}
			Logger.warn("No data for: " + id);
			return simpleId;
		}
		JsonNode json = Optional.ofNullable(org.findValue("alternateName"))
				.orElse(Json.toJson(Arrays.asList(org.findValue("name"))));
		String label = HtmlEscapers.htmlEscaper()
				.escape(json == null ? "" : last(json.elements()).asText());
		Logger.trace("Get org label, {} -> {} -> {}", id, simpleId, label);
		return label.isEmpty() ? simpleId : label;
	}

	private static JsonNode last(Iterator<JsonNode> iterator) {
		JsonNode result = null;
		while (iterator.hasNext()) {
			result = iterator.next();
		}
		return result;
	}

	/**
	 * @param id A Lobid-Resources URI or hbz title ID
	 * @return A human readable label for the given id
	 */
	public static String resourceLabel(String id) {
		Callable<String> getLabel = () -> {
			// e.g. take TT000086525 from http://lobid.org/resources/TT000086525#!
			String simpleId =
					id.replaceAll("https?://[^/]+/resources?/(.+?)[^A-Z0-9]*$", "$1");
			JsonNode json = getResource(simpleId).findValue("title");
			String label =
					json == null ? "" : HtmlEscapers.htmlEscaper().escape(json.asText());
			Logger.debug("Get res label, {} -> {} -> {}", id, simpleId, label);
			return label.isEmpty() ? simpleId : label;
		};
		try {
			return Cache.getOrElse("res.label." + id, getLabel, Application.ONE_DAY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * @param q The query
	 * @return Main headings for q
	 */
	public static List<String> gndMainHeadings(String q) {
		List<JsonNode> gndUris = WS.url("http://lobid.org/gnd/search")
				.setHeader("Accept", "application/json")
				.setQueryParameter("q", "variantName:" + q)
				.setQueryParameter("filter", "type:(NOT (ConferenceOrEvent Work))")
				.setQueryParameter("size", "500").get()
				.map((WSResponse response) -> Lists
						.newArrayList(response.asJson().get("member").elements()).stream()
						.map(node -> node.get("id")).collect(Collectors.toList()))
				.get(Lobid.API_TIMEOUT);
		return gndUris.stream()//
				.map(gndUri -> Pair.of(gndUri.textValue(), hitsInNwbib(gndUri)))
				.filter(uriAndHits -> uriAndHits.getRight() > 5)
				.sorted(
						Collections.reverseOrder(Comparator.comparingLong(Pair::getRight)))
				.map(pair -> Lobid.gndLabel(pair.getLeft()))
				.filter(label -> !label.equalsIgnoreCase(q))//
				.limit(3).collect(Collectors.toList());
	}

	private static Long hitsInNwbib(JsonNode r) {
		return getTotalHits("subject.componentList.id", r.textValue(), "")
				.get(API_TIMEOUT);
	}

	private static String gndLabel(String uri) {
		String cacheKey = "gnd.label." + uri;
		final String cachedResult = (String) Cache.get(cacheKey);
		if (cachedResult != null) {
			return cachedResult;
		}
		WSRequest requestHolder = WS.url(Application.CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("q", "subject.componentList.id:" + escapeUri(uri))
				.setQueryParameter("format", "json").setQueryParameter("size", "1");
		return requestHolder.get().map((WSResponse response) -> {
			JsonNode value = response.asJson();
			String label = findSubjectLabelForUriInResponse(uri, value);
			Cache.set(cacheKey, label, Application.ONE_DAY);
			return label;
		}).get(Lobid.API_TIMEOUT);
	}

	private static String findSubjectLabelForUriInResponse(String uri,
			JsonNode json) {
		List<JsonNode> complexSubjects = json.findValues("componentList");
		String label =
				complexSubjects.stream()
						.flatMap((complexSubject) -> StreamSupport.stream(Spliterators
								.spliteratorUnknownSize(complexSubject.elements(), 0), false))
						.filter((s) -> s.has("id") && s.get("id").textValue().equals(uri))
						.findFirst().map((s) -> s.get("label").textValue()).orElse(uri);
		return label;
	}

	private static String nwBibLabel(String uri) {
		String cacheKey = "nwbib.label." + uri;
		final String cachedResult = (String) Cache.get(cacheKey);
		if (cachedResult != null) {
			return cachedResult;
		}
		Type type = uri.contains("spatial") ? Classification.Type.SPATIAL
				: Classification.Type.NWBIB;
		String label = Classification.label(uri, type);
		label = HtmlEscapers.htmlEscaper().escape(label);
		label = label.trim().isEmpty() ? uri : label;
		Cache.set(cacheKey, label, Application.ONE_DAY);
		return label;
	}

	/**
	 * @param q Query to search in all fields
	 * @param person Query for a person associated with the resource
	 * @param name Query for the resource name (title)
	 * @param subject Query for the resource subject
	 * @param id Query for the resource id
	 * @param publisher Query for the resource publisher
	 * @param issued Query for the resource issued year
	 * @param medium Query for the resource medium
	 * @param nwbibspatial Query for the resource nwbibspatial classification
	 * @param nwbibsubject Query for the resource nwbibsubject classification
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param field The facet field (the field to facet over)
	 * @param location A polygon describing the subject area of the resources
	 * @param word A word, a concept from the hbz union catalog
	 * @param corporation A corporation associated with the resource
	 * @param raw A query string that's directly (unprocessed) passed to ES
	 * @return A JSON representation of the requested facets
	 */
	public static Promise<JsonNode> getFacets(String q, String person,
			String name, String subject, String id, String publisher, String issued,
			String medium, String nwbibspatial, String nwbibsubject, String owner,
			String field, String t, String location, String word, String corporation,
			String raw) {
		WSRequest request = WS.url(Application.CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json").setQueryParameter("name", name)
				.setQueryParameter("publisher", publisher)//
				.setQueryParameter("id", id)//
				.setQueryParameter("aggregations", field.split("<")[0])//
				.setQueryParameter("from", "0")//
				.setQueryParameter("size",
						field.equals(Application.ITEM_FIELD) ? "9999"
								: Application.MAX_FACETS + "")
				.setQueryParameter("medium", medium)//
				.setQueryParameter("location", locationPolygon(location))//
				.setQueryParameter("issued", issued)//
				.setQueryParameter("filter",
						Application.CONFIG.getString("nwbib.filter"))//
				.setQueryParameter("t", t);

		if (!person.isEmpty())
			request = request.setQueryParameter("agent", person);
		else if (!corporation.isEmpty())
			request = request.setQueryParameter("agent", corporation);

		if (!nwbibsubject.isEmpty() && !subject.isEmpty())
			request = request.setQueryParameter("subject",
					subjectAndNwbibSubject(subject, nwbibsubject));

		if (!nwbibsubject.isEmpty() && subject.isEmpty())
			request = request.setQueryParameter("subject", nwbibsubject);

		if (!raw.isEmpty()
				&& !raw.contains(Lobid.escapeUri(Application.COVERAGE_FIELD)))
			request = request.setQueryParameter("q", raw);

		request = setupWordParameter(q, nwbibspatial, word, request);

		if (!field.equals(Application.ITEM_FIELD))
			request = request.setQueryParameter("owner", owner);
		if (!field.startsWith("http") && nwbibsubject.isEmpty())
			request = request.setQueryParameter("subject", subject);

		String url = request.getUrl();
		Map<String, Collection<String>> parameters = request.getQueryParameters();
		Logger.debug("Facets request URL {}, query params {} ", url, parameters);
		return request.get().map((WSResponse response) -> {
			if (response.getStatus() == Http.Status.OK) {
				return response.asJson();
			}
			Logger.warn("{}: {} ({}, {})", response.getStatus(),
					response.getStatusText(), url, parameters);
			return Json.toJson(ImmutableMap.of("entries", Arrays.asList(), "field",
					field, "count", 0));
		});
	}

	private static String subjectAndNwbibSubject(String subject,
			String nwbibsubject) {
		return subject + "," + nwbibsubject + ",AND";
	}

	private static String setUpNwbibspatial(String nwbibspatial) {
		String query = Arrays.asList(nwbibspatial.replace(",AND", "").split(","))
				.stream().map(id -> "spatial.id:\"" + id + "\"")
				.collect(Collectors.joining(" AND "));
		return query;
	}

	private static String preprocess(final String q) {
		String result;
		if (q.trim().isEmpty() || q.matches(".*?([+~]|AND|OR|\\s-|\\*|:).*?")) {
			// if supported query string syntax is used, leave it alone:
			result = q;
		} else {
			// else prepend '+' to all terms for AND search:
			result = Arrays.asList(q.split("[\\s-]")).stream().map(x -> "+" + x)
					.collect(Collectors.joining(" "));
		}
		return result// but escape unsupported query string syntax:
				.replace("\\", "\\\\").replace("^", "\\^").replace("&&", "\\&&")
				.replace("||", "\\||").replace("!", "\\!").replace("(", "\\(")
				.replace(")", "\\)").replace("{", "\\{").replace("}", "\\}")
				.replace("[", "\\[").replace("]", "\\]")
				// `embedded` phrases, like foo"something"bar -> foo\"something\"bar
				// .replaceAll("([^\\s])\"([^\"]+)\"([^\\s])", "$1\\\\\"$2\\\\\"$3")
				// remove inescapable range query symbols, possibly prepended with `+`:
				.replaceAll("^\\+?<", "").replace("^\\+?>", "");
	}

	private static final Map<String, String> keys = ImmutableMap.of(//
			Application.TYPE_FIELD, "type.labels", //
			Application.MEDIUM_FIELD, "medium.labels");

	/**
	 * @param types Some type URIs
	 * @return An icon CSS class for the URIs
	 */
	public static String typeIcon(List<String> types) {
		return facetIcon(types, Application.TYPE_FIELD);
	}

	/**
	 * @param types Some type URIs
	 * @return A human readable label for the URIs
	 */
	public static String typeLabel(List<String> types) {
		return facetLabel(types, Application.TYPE_FIELD, "Publikationstypen");
	}

	/**
	 * @param queryValues The value string of the query, e.g. <br/>
	 *          `"Eisenbahnlinie,https://d-nb.info/gnd/4129465-8"`
	 * @return The given string, without URIs, e.g.`"Eisenbahnlinie"`
	 */
	public static String withoutUris(String queryValues) {
		return Arrays.asList(queryValues.split(",")).stream()
				.filter(s -> !s.startsWith("http") && !s.matches("AND|OR"))
				.collect(Collectors.joining(","));
	}

	/**
	 * @param uris Some URIs
	 * @param field The ES field to facet over
	 * @param label A label for the facet
	 * @return A human readable label for the URIs
	 */
	public static String facetLabel(List<String> uris, String field,
			String label) {
		if (uris.size() == 1 && uris.get(0).contains(",") && !label.isEmpty()) {
			int length = Arrays.asList(uris.get(0).split(",")).stream()
					.filter(s -> !s.trim().isEmpty()).toArray().length;
			return String.format("%s: %s ausgewählt", label, length);
		}
		if (uris.size() == 1 && isOrg(uris.get(0))) {
			return Lobid.organisationLabel(uris.get(0));
		} else if (uris.size() == 1
				&& (isNwBibClass(uris.get(0)) || isNwBibSpatial(uris.get(0))))
			return Lobid.nwBibLabel(uris.get(0));
		else if (uris.size() == 1 && isGnd(uris.get(0)))
			return Lobid.gndLabel(uris.get(0));
		else if (uris.size() == 1 && isWikidata(uris.get(0)))
			return WikidataLocations.label(uris.get(0));
		String configKey = keys.getOrDefault(field, "");

		String type = selectType(uris, configKey);
		if (type.isEmpty())
			return "";
		@SuppressWarnings("unchecked")
		List<String> details = configKey.isEmpty() ? uris
				: ((List<String>) Application.CONFIG.getObject(configKey).unwrapped()
						.get(type));
		if (details == null || details.size() < 1)
			return type;
		String selected = details.get(0).replace("<", "&lt;").replace(">", "&gt;");
		return selected.isEmpty() ? uris.get(0) : selected;
	}

	/**
	 * @param uris Some URIs
	 * @param field The ES field to facet over
	 * @return An icon CSS class for the given URIs
	 */
	public static String facetIcon(List<String> uris, String field) {
		if ((uris.size() == 1 && isOrg(uris.get(0)))
				|| field.equals(Application.ITEM_FIELD))
			return "octicon octicon-home";
		else if ((uris.size() == 1 && isNwBibClass(uris.get(0)))
				|| field.equals(Application.NWBIB_SUBJECT_FIELD))
			return "octicon octicon-list-unordered";
		else if ((uris.size() == 1 && isNwBibSpatial(uris.get(0)))
				|| field.equals(Application.NWBIB_SPATIAL_FIELD)
				|| field.equals(Application.COVERAGE_FIELD))
			return "octicon octicon-milestone";
		else if ((uris.size() == 1 && isGnd(uris.get(0)))
				|| field.equals(Application.SUBJECT_FIELD))
			return "octicon octicon-tag";
		else if (field.equals(Application.ISSUED_FIELD))
			return "glyphicon glyphicon-asterisk";
		String configKey = keys.getOrDefault(field, "");
		String type = selectType(uris, configKey);
		if (type.isEmpty())
			return "";
		@SuppressWarnings("unchecked")
		List<String> details = configKey.isEmpty() ? uris
				: (List<String>) Application.CONFIG.getObject(configKey).unwrapped()
						.get(type);
		if (details == null || details.size() < 2)
			return type;
		String selected = details.get(1);
		return selected.isEmpty() ? uris.get(0) : selected;
	}

	/**
	 * @param types The type uris associated with a resource
	 * @param configKey The key from the config file (icons or labels)
	 * @return The most specific of the passed types
	 */
	public static String selectType(List<String> types, String configKey) {
		if (configKey.isEmpty())
			return types.get(0);
		Logger.trace("Types: " + types);
		@SuppressWarnings("unchecked")
		List<Pair<String, Integer>> selected = types.stream().map(t -> {
			List<Object> vals = ((List<Object>) Application.CONFIG
					.getObject(configKey).unwrapped().get(t));
			if (vals == null)
				return Pair.of(t, 0);
			Integer specificity = (Integer) vals.get(2);
			return ((String) vals.get(0)).isEmpty()
					|| ((String) vals.get(1)).isEmpty() //
							? Pair.of("", specificity)
							: Pair.of(t, specificity);
		}).filter(t -> {
			return !t.getLeft().isEmpty();
		}).collect(Collectors.toList());
		Collections.sort(selected, (a, b) -> b.getRight().compareTo(a.getRight()));
		Logger.trace("Selected: " + selected);
		return selected.isEmpty() ? ""
				: selected.get(0).getLeft().contains("Miscellaneous")
						&& selected.size() > 1 ? selected.get(1).getLeft()
								: selected.get(0).getLeft();
	}

	static boolean isOrg(String term) {
		return term.contains("lobid.org/organisation");
	}

	static boolean isNwBibClass(String term) {
		return term.startsWith("http://purl.org/lobid/nwbib#")
				|| term.startsWith("https://nwbib.de/subjects#");
	}

	private static boolean isNwBibSpatial(String term) {
		return term.startsWith("http://purl.org/lobid/nwbib-spatial#")
				|| term.startsWith("https://nwbib.de/spatial#");
	}

	private static boolean isGnd(String term) {
		return term.startsWith(GND_PREFIX);
	}

	private static String locationPolygon(String location) {
		return location.contains("|") ? location.split("\\|")[1] : location;
	}

	/**
	 * @param doc The result JSON doc
	 * @return A mapping of ISILs to item URIs
	 */
	public static Map<String, List<String>> items(String doc) {
		JsonNode items = Json.parse(doc).findValue("hasItem");
		Map<String, List<String>> result = new HashMap<>();
		if (items != null && (items.isArray() || items.isTextual()))
			mapIsilsToUris(items, result);
		return result;
	}

	private static void mapIsilsToUris(JsonNode items,
			Map<String, List<String>> result) {
		Iterator<JsonNode> elements =
				items.isArray() ? items.elements() : Arrays.asList(items).iterator();
		while (elements.hasNext()) {
			String itemUri = elements.next().get("id").asText();
			try {
				String isil = itemUri.split(":")[2];
				List<String> uris = result.getOrDefault(isil, new ArrayList<>());
				uris.add(itemUri);
				result.put(isil, uris);
			} catch (ArrayIndexOutOfBoundsException x) {
				Logger.error(x.getMessage());
			}
		}
	}

	/**
	 * @param itemUri The lobid item URI
	 * @return The OPAC URL for the given item, or null
	 */
	public static String opacUrl(String itemUri) {
		try (InputStream stream =
				Play.application().resourceAsStream("isil2opac_hbzid.json")) {
			JsonNode json = Json.parse(stream);
			String[] hbzId_isil_sig =
					itemUri.substring(itemUri.indexOf("items/") + 6).split(":");
			String hbzId = hbzId_isil_sig[0];
			String isil = hbzId_isil_sig[1];
			Logger.debug("From item URI {}, got ISIL {} and HBZ-ID {}", itemUri, isil,
					hbzId);
			JsonNode urlTemplate = json.get(isil);
			if (urlTemplate != null)
				return urlTemplate.asText().replace("{hbzid}", hbzId);
		} catch (IOException e) {
			Logger.error("Could not create OPAC URL", e);
		}
		return null;
	}

	/**
	 * Compare ISILs for sorting.
	 *
	 * @param i1 The first ISIL
	 * @param i2 The second ISIL
	 * @return True, if i1 should come before i2
	 */
	public static boolean compareIsil(String i1, String i2) {
		String[] all1 = i1.split("-");
		String[] all2 = i2.split("-");
		if (all1.length == 3 && all2.length == 3) {
			if (all1[1].equals(all2[1])) {
				// use secondary if main is equal, e.g. DE-5-11 before DE-5-20
				return numerical(all1[2]) < numerical(all2[2]);
			}
		} else if (all1[1].equals(all2[1])) {
			// same main sigel, prefer shorter, e.g. DE-5 before DE-5-11
			return all1.length < all2.length;
		}
		// compare by main sigel, e.g. DE-5 before DE-6:
		return numerical(all1[1]) < numerical(all2[1]);
	}

	private static int numerical(String s) {
		// replace non-digits with 9, e.g. for DE-5 before DE-Walb1
		return Integer.parseInt(s.replaceAll("\\D", "9"));
	}
}
