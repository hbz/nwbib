package controllers.nwbib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	private static final Map<String, String> WIKIDATA_LABELS = new HashMap<>();

	static {
		load().elements().forEachRemaining(item -> {
			String id = item.get("item").get("value").textValue();
			String label = item.get("itemLabel").get("value").textValue();
			WIKIDATA_LABELS.put(Classification.toNwbibNamespace(id), label);
		});

	}

	/**
	 * @param uri The Wikidata URI
	 * @return The Wikidata label for the URI
	 */
	public static String label(String uri) {
		String label = WIKIDATA_LABELS.get(uri);
		return label == null ? uri : label;
	}

	/**
	 * @return The Wikidata locations as JSON
	 */
	public static JsonNode load() {
		try {
			File jsonFile = wikidataFile();
			if (!jsonFile.exists()) {
				// TODO Don't block, return Promise (but /classification no Promise yet)
				WSResponse wsResponse =
						wikidataSparqlQuery("conf/wikidata.sparql", "json")
								.get(Integer.MAX_VALUE);
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

	/**
	 * @param file The location of the SPARQL query file, relative to the project
	 * @param format The result format (json or csv)
	 * @return A response promise
	 * @throws IOException If the given file could not be read
	 */
	public static Promise<WSResponse> wikidataSparqlQuery(String file,
			String format) throws IOException {
		File sparqlFile = Play.application().getFile(file);
		String sparqlString = Files.readAllLines(Paths.get(sparqlFile.toURI()))
				.stream().collect(Collectors.joining("\n"));
		Logger.info("Getting data from Wikidata, using query: \n{}", sparqlString);
		return cachedRequest(sparqlString,
				WS.url("https://query.wikidata.org/sparql")
						.setQueryParameter("query", sparqlString)
						.setHeader("Accept", format.equals("csv") ? "text/csv"
								: "application/sparql-results+json"));
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

}
