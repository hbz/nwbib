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
import play.libs.ws.WSRequestHolder;
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
				Iterator<JsonNode> elements = request().get(Integer.MAX_VALUE).asJson()
						.findValue("bindings").elements();
				List<JsonNode> items = new ArrayList<>();
				elements.forEachRemaining(items::add);
				JsonNode all = Json.toJson(items);
				try (FileWriter fw = new FileWriter(jsonFile)) {
					fw.write(pretty(all));
				}
				return all;
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
			WSRequestHolder request) {
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
				String baseUrl = "http://stage.lobid.org/resources/search";
				String qParamValue = String.format("spatial.id:\"%s\"", id);
				String fullUrl = baseUrl + "?q=" + qParamValue;
				Thread.sleep(35); // slight delay to avoid too much load on server
				WSResponse response =
						WS.url(baseUrl).setQueryParameter("q", qParamValue)
								.setQueryParameter("format", "json").execute().get(60 * 1000);
				if (response.getStatus() == Http.Status.OK) {
					JsonNode json = response.asJson();
					requestCounter++;
					long hits = json.get("totalItems").longValue();
					String coverage = collectCoverageValues(json.findValues("coverage"));
					if (requestCounter == 1 || requestCounter % 500 == 0) {
						Logger.debug("Request {}", requestCounter);
					}
					Logger.trace("{}: {} -> {} hits, coverage: {}", requestCounter, id,
							hits, coverage);
					String html = String.format(
							"<a title='Nach Titeln suchen' href='%s'><span class='glyphicon glyphicon-search'></span></a> (%s | %s)",
							fullUrl, hits, coverage);
					return hits == 0 ? "" : html;
				}
				Logger.error("Response status: {}: {}", response.getStatusText(),
						response.getBody());
				return "";
			}, cacheDuration);
		} catch (Exception e) {
			Logger.error("Could not query for " + id, e);
			return "";
		}

	}

	private static String collectCoverageValues(List<JsonNode> coverageNodes) {
		return coverageNodes.stream()
				.flatMap((JsonNode cs) -> Lists.newArrayList(cs.elements()).stream())
				.map(c -> c.textValue().replace("<", "&lt;").replace(">", "&gt;"))
				.distinct().collect(Collectors.joining(", "));
	}

}
