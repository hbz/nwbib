package controllers.nwbib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.common.lang3.tuple.Pair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

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
 * @author Fabian Steeg (fsteeg)
 *
 */
public class WikidataLocations {

	private static final int ONE_DAY = 60 * 60 * 24;
	private static int requestCounter;

	/**
	 * @return The Wikidata locations as JSON
	 */
	public static JsonNode load() {
		requestCounter = 0;
		try {
			File jsonFile = wikidataFile();
			if (!jsonFile.exists()) {
				// TODO Don't block, return Promise (but /classification no Promise yet)
				WSResponse wsResponse = request().get(Integer.MAX_VALUE);
				if (wsResponse.getStatus() == Http.Status.OK) {
					Iterator<JsonNode> elements =
							wsResponse.asJson().findValue("bindings").elements();
					List<JsonNode> items = new ArrayList<>();
					elements.forEachRemaining(items::add);
					JsonNode all = Json.toJson(items);
					try (FileWriter fw = new FileWriter(jsonFile)) {
						fw.write(pretty(all));
					}
					return all;
				}
				Logger.error("Could not call Wikidata API: {}, body:\n{}",
						wsResponse.getStatusText(), wsResponse.getBody());
				return Json.newObject();
			}
			try (FileInputStream stream = new FileInputStream(jsonFile)) {
				return Json.parse(stream);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	static File wikidataFile() {
		return Play.application().getFile("conf/wikidata.json");
	}

	private static String pretty(JsonNode x) {
		try {
			return new ObjectMapper().writer().withDefaultPrettyPrinter()
					.writeValueAsString(x);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return x.toString();
	}

	private static Promise<WSResponse> request() throws IOException {
		File sparqlFile = Play.application().getFile("conf/wikidata.sparql");
		String sparqlString = Files.readAllLines(Paths.get(sparqlFile.toURI()))
				.stream().collect(Collectors.joining("\n"));
		Logger.info("Getting data from Wikidata, using query: \n{}", sparqlString);
		return cachedRequest(sparqlString,
				WS.url("https://query.wikidata.org/sparql")
						.setQueryParameter("query", sparqlString)
						.setQueryParameter("format", "json"));
	}

	private static Promise<WSResponse> cachedRequest(String key,
			WSRequest request) {
		@SuppressWarnings("unchecked")
		Promise<WSResponse> promise = (Promise<WSResponse>) Cache.get(key);
		if (promise == null) {
			promise = request.get();
			promise.onRedeem(response -> {
				if (response.getStatus() == Http.Status.OK) {
					Cache.set(key, Promise.pure(response), ONE_DAY);
				}
			});
		}
		return promise;
	}

	// Prototype, see https://github.com/hbz/nwbib/issues/392
	@SuppressWarnings("javadoc")
	public static String searchLink(String id) {
		try {
			int cacheDuration = Integer.MAX_VALUE;
			return Cache.getOrElse("spatialQuery." + id, () -> {
				String baseUrl = "http://lobid.org/resources/search";
				String q = String.format("spatial.id:\"%s\"", id);
				String fullUrl = baseUrl + "?q=" + q;
				Pair<String, Long> coverageAndHits = coverageAndHits(baseUrl, q);
				String html = String.format(
						"<a title='Nach Titeln suchen | %s' href='%s'><span class='glyphicon glyphicon-search'></span></a> %s",
						coverageAndHits.getLeft(), fullUrl, coverageAndHits.getRight());
				return coverageAndHits.getRight() > 0L ? html : "";
			}, cacheDuration);
		} catch (Exception e) {
			Logger.error("Could not query for " + id, e);
			return "";
		}
	}

	private static Pair<String, Long> coverageAndHits(String baseUrl, String q) {
		try {
			WSResponse response = WS.url(baseUrl).setQueryParameter("q", q)
					.setQueryParameter("format", "json").execute().get(1000);
			if (response.getStatus() == Http.Status.OK) {
				JsonNode json = response.asJson();
				requestCounter++;
				long hits = json.get("totalItems").longValue();
				String coverage = collectCoverageValues(json.findValues("coverage"));
				if (requestCounter == 1 || requestCounter % 500 == 0) {
					Logger.debug("Request {}", requestCounter);
				}
				Logger.trace("{}: {} -> {} hits, coverage: {}", requestCounter, q, hits,
						coverage);
				return Pair.of(coverage, hits);
			}
			Logger.error("Response not OK, status: {}: {}", response.getStatusText(),
					response.getBody());
		} catch (Exception x) {
			Logger.error("Error for q={}: {}", q, x.getMessage());
		}
		return Pair.of("", 0L);
	}

	private static String collectCoverageValues(List<JsonNode> coverageNodes) {
		return coverageNodes.stream()
				.flatMap((JsonNode cs) -> Lists.newArrayList(cs.elements()).stream())
				.map(c -> c.textValue().replace("<", "&lt;").replace(">", "&gt;"))
				.distinct().collect(Collectors.joining(", "));
	}

}
