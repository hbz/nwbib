/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static controllers.nwbib.Application.CONFIG;
import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Classification;
import controllers.nwbib.Classification.Type;
import controllers.nwbib.Lobid;
import play.libs.Json;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaTest
 */
@SuppressWarnings("javadoc")
public class ApplicationTest {

	@Test
	public void shortClassificationId() {
		assertThat(Classification.shortId("https://nwbib.de/subjects#N58206"))
				.as("short classification").isEqualTo("58206");
	}

	@Test
	public void shortSpatialClassificationId() {
		assertThat(Classification.shortId("https://nwbib.de/spatial#N58"))
				.as("short spatial classification").isEqualTo("58");
	}

	@Test
	public void classificationLabelNotAvailable() {
		assertThat(
				Classification.label("https://nwbib.de/spatial#N58", Type.SPATIAL))
						.as("empty label").isEqualTo("");
	}

	@Test
	public void typeSelectionMultiVolumeBook() {
		String selected = Lobid.selectType(
				Arrays.asList("BibliographicResource", "MultiVolumeBook", "Book"),
				"type.labels");
		assertThat(selected).isEqualTo("MultiVolumeBook");
	}

	@Test
	public void typeSelectionPublishedScore() {
		String selected = Lobid.selectType(
				Arrays.asList("MultiVolumeBook", "PublishedScore", "Book"),
				"type.labels");
		assertThat(selected).isEqualTo("PublishedScore");
	}

	@Test
	public void typeSelectionEditedVolume() {
		String selected = Lobid.selectType(Arrays.asList("MultiVolumeBook",
				"BibliographicResource", "EditedVolume"), "type.labels	");
		assertThat(selected).isEqualTo("EditedVolume");
	}

	@Test
	public void classificationNwbibsubject()
			throws MalformedURLException, IOException {
		List<String> nwbibsubjects = Classification
				.toJsonLd(new URL(CONFIG.getString("index.data.nwbibsubject")));
		nwbibsubjects.forEach(System.out::println);
		assertThat(nwbibsubjects.size()).isGreaterThan(1000);
		assertThat(nwbibsubjects.toString()).contains("Allgemeine Landeskunde")
				.contains("Landesbeschreibungen").contains("Reiseberichte");
	}

	@Test
	public void classificationNwbibspatial()
			throws MalformedURLException, IOException {
		List<String> nwbibspatials = Classification
				.toJsonLd(new URL(CONFIG.getString("index.data.nwbibspatial")));
		nwbibspatials.forEach(System.out::println);
		assertThat(nwbibspatials.size()).isGreaterThan(60);
		assertThat(nwbibspatials.toString()).contains("Nordrhein-Westfalen")
				.contains("Rheinland").contains("Grafschaft, Herzogtum Jülich");
	}

	@Test
	public void sortArabicNumerals() {
		JsonNode[] in = new JsonNode[] { //
				Json.newObject().put("label", "Stadtbezirk 10"),
				Json.newObject().put("label", "Stadtbezirk 9"),
				Json.newObject().put("label", "Stadtbezirk 8"),
				Json.newObject().put("label", "Stadtbezirk 7"),
				Json.newObject().put("label", "Stadtbezirk 6"),
				Json.newObject().put("label", "Stadtbezirk 5"),
				Json.newObject().put("label", "Stadtbezirk 4"),
				Json.newObject().put("label", "Stadtbezirk 3"),
				Json.newObject().put("label", "Stadtbezirk 2"),
				Json.newObject().put("label", "Stadtbezirk 1") };
		JsonNode[] correct = new JsonNode[] { //
				Json.newObject().put("label", "Stadtbezirk 1"),
				Json.newObject().put("label", "Stadtbezirk 2"),
				Json.newObject().put("label", "Stadtbezirk 3"),
				Json.newObject().put("label", "Stadtbezirk 4"),
				Json.newObject().put("label", "Stadtbezirk 5"),
				Json.newObject().put("label", "Stadtbezirk 6"),
				Json.newObject().put("label", "Stadtbezirk 7"),
				Json.newObject().put("label", "Stadtbezirk 8"),
				Json.newObject().put("label", "Stadtbezirk 9"),
				Json.newObject().put("label", "Stadtbezirk 10") };
		Arrays.sort(in, Classification.comparator);
		Assert.assertArrayEquals(correct, in);
	}

	@Test
	public void sortRomanNumerals() {
		JsonNode[] in = new JsonNode[] { //
				Json.newObject().put("label", "Stadtbezirk X"),
				Json.newObject().put("label", "Stadtbezirk IX"),
				Json.newObject().put("label", "Stadtbezirk VIII"),
				Json.newObject().put("label", "Stadtbezirk VII"),
				Json.newObject().put("label", "Stadtbezirk VI"),
				Json.newObject().put("label", "Stadtbezirk V"),
				Json.newObject().put("label", "Stadtbezirk IV"),
				Json.newObject().put("label", "Stadtbezirk III"),
				Json.newObject().put("label", "Stadtbezirk II"),
				Json.newObject().put("label", "Stadtbezirk I") };
		JsonNode[] correct = new JsonNode[] { //
				Json.newObject().put("label", "Stadtbezirk I"),
				Json.newObject().put("label", "Stadtbezirk II"),
				Json.newObject().put("label", "Stadtbezirk III"),
				Json.newObject().put("label", "Stadtbezirk IV"),
				Json.newObject().put("label", "Stadtbezirk V"),
				Json.newObject().put("label", "Stadtbezirk VI"),
				Json.newObject().put("label", "Stadtbezirk VII"),
				Json.newObject().put("label", "Stadtbezirk VIII"),
				Json.newObject().put("label", "Stadtbezirk IX"),
				Json.newObject().put("label", "Stadtbezirk X") };
		Arrays.sort(in, Classification.comparator);
		Assert.assertArrayEquals(correct, in);
	}

}
