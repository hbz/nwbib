/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Application;
import controllers.nwbib.Classification;
import controllers.nwbib.Lobid;
import controllers.nwbib.WikidataLocations;
import play.libs.F.Promise;
import play.mvc.Http;
import play.test.Helpers;
import play.twirl.api.Content;
import views.ReverseGeoLookup;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
public class ExternalIntegrationTest {

	@Before
	public void setUp() throws Exception {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		play.api.mvc.RequestHeader header = mock(play.api.mvc.RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context =
				new Http.Context(2L, header, request, flashData, flashData, argData);
		Http.Context.current.set(context);
	}

	@Ignore // https://github.com/hbz/nwbib/issues/633
	@Test
	public void testFacets() {
		running(testServer(3333), () -> {
			String field = Application.TYPE_FIELD;
			Promise<JsonNode> jsonPromise = Lobid.getFacets("köln", "", "", "", "",
					"", "", "", "", "", "", field, "", "", "", "", "");
			JsonNode facets = jsonPromise.get(Lobid.API_TIMEOUT);
			assertThat(facets.findValues("key").stream().map(e -> e.asText())
					.collect(Collectors.toList())).contains("BibliographicResource",
							"Article", "Book", "Periodical", "MultiVolumeBook", "Thesis",
							"Miscellaneous", "Proceedings", "EditedVolume", "Biography",
							"Festschrift", "Newspaper", "Bibliography", "Series",
							"OfficialPublication", "ReferenceSource", "PublishedScore",
							"Legislation", "Image", "Game");
			assertThat(facets.findValues("count").stream().map(e -> e.intValue())
					.collect(Collectors.toList())).excludes(0);
		});
	}

	@Test
	public void renderTemplate() {
		String query = "buch";
		int from = 0;
		int size = 10;
		running(testServer(3333), () -> {
			Content html = views.html.search.render("[{}]", query, "", "", "", "", "",
					"", "", "", "", from, size, 0L, "", "", "", "", "", "", "");
			assertThat(html.contentType()).isEqualTo("text/html");
			String text = Helpers.contentAsString(html);
			assertThat(text).contains("NWBib").contains("buch")
					.contains("Sachgebiete").contains("Regionen");
		});
	}

	@Ignore // https://github.com/hbz/nwbib/issues/633
	@Test
	public void sizeRequest() {
		running(testServer(3333), () -> {
			Long hits = Lobid
					.getTotalHits("isPartOf.hasSuperordinate.id",
							"http://lobid.org/resource/HT018486420#!", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).isGreaterThan(0);
			hits = Lobid
					.getTotalHits("isPartOf.hasSuperordinate.id",
							"http://lobid.org/resources/HT002091108#!", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).isGreaterThan(0);
			hits = Lobid
					.getTotalHits("containedIn.id",
							"http://lobid.org/resource/HT001387709#!", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).isGreaterThan(0);
		});
	}

	@Test
	@Ignore // https://wdq.wmflabs.org/api is gone
	public void reverseGeoLookup() {
		running(testServer(3333), () -> {
			assertEquals("Menden (Sauerland)",
					ReverseGeoLookup.of("51.433333391323686,7.800000105053186"));
		});
	}

	@Test
	public void classificationNwbibsubjectHierarchy() {
		running(testServer(3333), () -> {
			Pair<List<JsonNode>, Map<String, List<JsonNode>>> topAndSub =
					Classification.Type.from("Sachsystematik").buildHierarchy();
			String nwbib = "https://nwbib.de/subjects#";
			assertThat(topAndSub.getRight().get(nwbib + "N882000")).isNotNull();
			assertThat(topAndSub.getRight().get(nwbib + "N882000").size())
					.describedAs("N882000").isGreaterThan(1);
			assertThat(topAndSub.getRight().get(nwbib + "N884000")).isNull();
			assertThat(topAndSub.getRight().get(nwbib + "N880000")).isNotNull();
			assertThat(topAndSub.getRight().get(nwbib + "N880000").size())
					.describedAs("N880000").isGreaterThan(1);
		});
	}

	@Test
	public void classificationNwbibsubjectRegister() {
		running(testServer(3333), () -> {
			JsonNode register =
					Classification.Type.from("Sachsystematik").buildRegister();
			assertThat(register.toString()).contains("Audiovisuelle Medien")
					.contains("Publizistik. Information und Dokumentation - Allgemeines")
					.contains("Bibliotheksgeschichte").contains("Schulbibliotheken");
		});
	}

	@Test
	public void classificationWikidata() {
		running(testServer(3333), () -> {
			Pair<List<JsonNode>, Map<String, List<JsonNode>>> topAndSub =
					Classification.buildHierarchyWikidata(WikidataLocations.load());
			List<JsonNode> items =
					topAndSub.getRight().values().stream().flatMap(x -> x.stream())
							.filter(n -> n.toString().contains("Angermund"))
							.collect(Collectors.toList());
			assertThat(items.size()).isEqualTo(1).describedAs(items.toString());
		});
	}
}
