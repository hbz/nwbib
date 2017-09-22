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

	/**
	 * @return The Wikidata locations as JSON
	 */
	public static JsonNode load() {
		try {
			File jsonFile = Play.application().getFile("conf/wikidata.json");
			if (!jsonFile.exists()) {
				// TODO Don't block, return Promise
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
}
