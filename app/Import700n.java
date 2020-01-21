
/* Copyright 2019 Fabian Steeg, hbz. Licensed under the GPLv2 */

import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.jena.ext.com.google.common.collect.Streams;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Lobid;
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
	private static Function<JsonNode, String> toAlephImportValue =
			node -> String.format("\"%s$$0%s\"", //
					node.get("label").asText(), //
					node.get("id").asText());

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
					String resultLine = processLobidResource(record);
					// String resultLine = processNwbibSnapshot(record);
					System.out.println(resultLine);
					writer.write(resultLine + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	@SuppressWarnings("unused")
	private static String processNwbibSnapshot(JsonNode record) {
		String id = record.get("hbzId").asText();
		String result = processLobidResource(Lobid.getResource(id));
		// See https://github.com/hbz/nwbib/issues/516
		result = result
				.replace("\t",
						"\t\"Bistum Münster$$0https://nwbib.de/spatial#Q769380\", ")
				.replaceAll(", $", "");
		return result;
	}

	private static String processLobidResource(JsonNode record) {
		Stream<String> subjects = Streams.concat(//
				processSpatial(record), processSubject(record));
		String resultLine = String.format("%s\t%s", //
				record.get("hbzId").asText(), subjects.collect(Collectors.joining(", "))//
						// https://github.com/hbz/lobid-resources/issues/1018
						.replaceAll("spatial#N05", "spatial#N04"));
		resultLine = resultLine//
				.replace("spatial#N04$$0", "Westfalen$$0")
				.replace("Siebengebirge$$0https://nwbib.de/spatial#Q4236",
						"Siebengebirge$$0https://nwbib.de/spatial#N23")
				.replace(//
						"Kleinere geistliche Territorien im Rheinland$$0https://nwbib.de/spatial#N52", //
						"Kleinere Territorien im Rheinland$$0https://nwbib.de/spatial#N54");
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
		Stream<JsonNode> spatials = asStream(record.findValue("spatial"));
		return spatials == null ? Collections.<String> emptyList().stream()
				: spatials.filter(spatial -> spatial.has("id") && spatial.has("label")
				// https://github.com/hbz/nwbib/issues/523
						&& (!spatial.get("id").textValue().endsWith("N96")
								&& !spatial.get("id").textValue().endsWith("N97")))
						.map(toAlephImportValue);
	}

	private static Stream<JsonNode> asStream(JsonNode record) {
		if (record != null) {
			Iterable<JsonNode> iterable = () -> record.elements();
			return StreamSupport.stream(iterable.spliterator(), false);
		}
		return null;
	}

}
