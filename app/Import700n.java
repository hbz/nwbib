
/* Copyright 2019-2021 Fabian Steeg, hbz. Licensed under the GPLv2 */

import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.jena.ext.com.google.common.collect.Streams;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Classification;
import controllers.nwbib.Lobid;
import controllers.nwbib.WikidataLocations;
import play.libs.Json;

/**
 * Generate a file for import into field 700n.
 * 
 * See https://github.com/hbz/nwbib/issues/464
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Import700n {

	// see conf/700n-import.sh to obtain full input data
	private static File dataIn = new File("conf/700n-import-test.jsonl");
	private static File dataOut = new File("conf/700n-import-test.txt");
	private static Function<JsonNode, String> toAlephImportValue = node -> {
		String label = node.get("label").asText();
		String id = node.get("id").asText();
		// See https://github.com/hbz/nwbib/issues/540
		if (label.contains("spatial#")) {
			String newLabel = Classification.label(id, Classification.Type.SPATIAL);
			label = newLabel.isEmpty() ? WikidataLocations.label(id) : newLabel;
		}
		return String.format("\"%s$$0%s\"", label, id);
	};

	/**
	 * @param args Optional, the input (jsonl) and the output (txt) file names
	 */
	public static void main(String[] args) {
		running(testServer(3333), () -> {
			if (args.length == 2) {
				dataIn = new File(args[0]);
				dataOut = new File(args[1]);
			}
			try (Scanner scanner = new Scanner(dataIn, StandardCharsets.UTF_8.name());
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream(dataOut), StandardCharsets.UTF_8))) {
				while (scanner.hasNextLine()) {
					JsonNode record = Json.parse(scanner.nextLine());
					// String resultLine = processLobidResource(record);
					String resultLine = processNwbibSnapshot(record);
					if (resultLine != null) {
						System.out.println(resultLine);
						writer.write(resultLine + "\n");
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private static String processNwbibSnapshot(JsonNode record) {
		String id = record.get("hbzId").asText();
		JsonNode resource = Lobid.getResource(id);
		// See https://github.com/hbz/nwbib/issues/540#issuecomment-607698311
		if (resource.has("inCollection")
				&& resource.get("inCollection").findValues("id").toString()
						.contains("http://lobid.org/resources/HT014846970#!")) {
			// See https://github.com/hbz/nwbib/issues/465, we actually want ZDB here
			// return null;
		}
		String result = processLobidResource(resource);
		// See https://github.com/hbz/nwbib/issues/541
		result = result.replace("https://nwbib.de/spatial#Q881204",
				"https://nwbib.de/spatial#Q10936");
		return result;
	}

	private static String processLobidResource(JsonNode record) {
		Stream<String> subjects = Streams.concat(//
				processSpatial(record), processSubject(record));
		String resultLine = String.format("%s\t%s", //
				record.get("zdbId").asText(),
				subjects.collect(Collectors.joining(", ")));
		return resultLine;
	}

	private static Stream<String> processSubject(JsonNode record) {
		Stream<JsonNode> subjects = asStream(record.findValue("subject"));
		return subjects == null ? Collections.<String> emptyList().stream()
				: subjects
						.filter(subject -> subject.has("source")
								&& subject.get("source").get("id").textValue()
										.startsWith("https://nwbib.de")
								&& subject.has("id") && subject.has("label")
								// https://github.com/hbz/nwbib/issues/523
								&& (!subject.get("id").textValue().endsWith("N96")
										&& !subject.get("id").textValue().endsWith("N97")))
						.map(toAlephImportValue);
	}

	private static Stream<String> processSpatial(JsonNode record) {
		Supplier<Stream<JsonNode>> spatials =
				() -> asStream(record.findValue("spatial"));
		if (spatials.get() == null) {
			return Collections.<String> emptyList().stream();
		}
		return spatials.get()
				.filter(spatial -> spatial.has("id") && spatial.has("label")
						&& !isRedundantSuperordinate(spatial, spatials))
				.map(toAlephImportValue);
	}

	private static boolean isRedundantSuperordinate(JsonNode candidate,
			Supplier<Stream<JsonNode>> spatialsSupplier) {
		List<String> spatials = others(spatialsSupplier, candidate);
		if (spatials.isEmpty() || !candidate.has("notation")) {
			return false;
		}
		String id = candidate.get("id").textValue();
		for (String spatial : spatials) {
			boolean isRedundant = Classification.pathTo(spatial).contains(id);
			if (isRedundant)
				return true;
		}
		return false;
	}

	private static List<String> others(Supplier<Stream<JsonNode>> spatials,
			JsonNode spatial) {
		return spatials.get()
				.filter(s -> !s.has("notation")
						&& !s.get("id").textValue().equals(spatial.get("id").textValue()))
				.map(s -> s.get("id").textValue()).collect(Collectors.toList());
	}

	private static Stream<JsonNode> asStream(JsonNode record) {
		if (record != null) {
			Iterable<JsonNode> iterable = () -> record.elements();
			return StreamSupport.stream(iterable.spliterator(), false);
		}
		return null;
	}

}
