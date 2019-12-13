
/* Copyright 2019 Fabian Steeg, hbz. Licensed under the GPLv2 */

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
		if (args.length == 2) {
			dataIn = new File(args[0]);
			dataOut = new File(args[1]);
		}
		try (Scanner scanner = new Scanner(dataIn, StandardCharsets.UTF_8.name());
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(dataOut), StandardCharsets.UTF_8))) {
			while (scanner.hasNextLine()) {
				JsonNode record = Json.parse(scanner.nextLine());
				Stream<String> subjects = Streams.concat(//
						processSpatial(record), processSubject(record));
				String resultLine = String.format("%s\t%s", //
						record.get("hbzId").asText(),
						subjects.collect(Collectors.joining(", "))//
								// https://github.com/hbz/lobid-resources/issues/1018
								.replaceAll("spatial#N05", "spatial#N04"));
				resultLine = resultLine//
						.replace("spatial#N04$$0", "Westfalen$$0")
						.replace("Siebengebirge$$0https://nwbib.de/spatial#Q4236",
								"Siebengebirge$$0https://nwbib.de/spatial#N23");
				System.out.println(resultLine);
				writer.write(resultLine + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static Stream<String> processSubject(JsonNode record) {
		Stream<JsonNode> subjects = asStream(record.findValue("subject"));
		return subjects == null ? Collections.<String> emptyList().stream()
				: subjects.filter(subject -> subject.has("source")
						&& subject.get("source").get("id").textValue()
								.startsWith("https://nwbib.de")
						&& subject.has("id") && subject.has("label"))
						.map(toAlephImportValue);
	}

	private static Stream<String> processSpatial(JsonNode record) {
		Stream<JsonNode> spatials = asStream(record.findValue("spatial"));
		return spatials == null ? Collections.<String> emptyList().stream()
				: spatials.filter(spatial -> spatial.has("id") && spatial.has("label"))
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
