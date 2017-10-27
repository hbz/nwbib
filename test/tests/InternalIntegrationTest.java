/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Application;
import controllers.nwbib.Lobid;
import play.libs.F.Promise;
import play.mvc.Http;
import play.test.Helpers;
import play.twirl.api.Content;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
public class InternalIntegrationTest {

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

	@Test
	public void testFacets() {
		running(testServer(3333), () -> {
			String field = Application.TYPE_FIELD;
			Promise<JsonNode> jsonPromise = Lobid.getFacets("köln", "", "", "", "",
					"", "", "", "", "", "", field, "", "", "", "", "", "");
			JsonNode facets = jsonPromise.get(Lobid.API_TIMEOUT);
			assertThat(facets.findValue("aggregation").findValues("key").stream()
					.map(e -> e.asText()).collect(Collectors.toList())).contains(
							"BibliographicResource", "Article", "Book", "MultiVolumeBook",
							"Thesis", "Miscellaneous", "Proceedings", "EditedVolume",
							"Biography", "Festschrift", "Newspaper", "Bibliography", "Series",
							"OfficialPublication", "ReferenceSource", "PublishedScore",
							"Legislation", "Game");
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
					"", "", "", "", from, size, 0L, "", "", "", "", "", "", "", "");
			assertThat(html.contentType()).isEqualTo("text/html");
			String text = Helpers.contentAsString(html);
			assertThat(text).contains("NWBib").contains("buch")
					.contains("Sachsystematik").contains("Raumsystematik");
		});
	}

	@Test
	public void sizeRequest() {
		running(testServer(3333), () -> {
			Long hits = Lobid
					.getTotalHits("isPartOf.hasSuperordinate.id",
							"http://lobid.org/resources/HT018486420#!", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).as("1").isGreaterThan(0);
			hits = Lobid
					.getTotalHits("isPartOf.hasSuperordinate.id",
							"http://lobid.org/resources/HT002091108#!", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).as("2").isGreaterThan(0);
			hits = Lobid
					.getTotalHits("containedIn.id",
							"http://lobid.org/resources/HT001387709#!", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).as("3").isGreaterThan(0);
		});
	}

}
