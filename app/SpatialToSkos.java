
/* Copyright 2019 Fabian Steeg, hbz. Licensed under the GPLv2 */

import static controllers.nwbib.WikidataLocations.wikidataSparqlQuery;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SKOS;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Classification;
import controllers.nwbib.Lobid;
import play.libs.ws.WSResponse;
import play.mvc.Http;

/**
 * Generate a SKOS representation from the internal spatial classification data
 * (which itself originates from a SKOS file, but is enriched from different
 * sources, see https://github.com/hbz/lobid-vocabs/issues/85)
 *
 * @author Fabian Steeg (fsteeg)
 *
 */
public class SpatialToSkos {

	private static final String NWBIB_SPATIAL = "https://nwbib.de/spatial";
	private static final String NWBIB_SPATIAL_NAMESPACE = NWBIB_SPATIAL + "#";

	// Temporary: also write a CSV file for Wikidata batch import, but without
	// items already imported, see https://github.com/hbz/nwbib/issues/469
	static List<String> done = new ArrayList<>();
	static List<String> toDo = new ArrayList<>();

	private static void initCsv() {
		try {
			WSResponse wsResponse =
					wikidataSparqlQuery("conf/qid-nwbib.sparql", "csv")
							.get(Integer.MAX_VALUE);
			if (wsResponse.getStatus() == Http.Status.OK) {
				for (CSVRecord record : CSVFormat.DEFAULT.withFirstRecordAsHeader()
						.parse(new StringReader(new String(wsResponse.asByteArray())))) {
					done.add(record.get("item")
							.substring("http://www.wikidata.org/entity/".length())
							+ ",\"\"\"\"" + record.get("nwbibId") + "\"");
				}
			}
			toDo.add("Q1198,\"\"\"\"N01\"");
			toDo.add("Q152243,\"\"\"\"N03\"");
			toDo.add("Q8614,\"\"\"\"N04\"");
			toDo.add("Q462011,\"\"\"\"N10\"");
			toDo.add("Q72931,\"\"\"\"N12\"");
			toDo.add("Q2036208,\"\"\"\"N13\"");
			toDo.add("Q4194,\"\"\"\"N14\"");
			toDo.add("Q580471,\"\"\"\"N16\"");
			toDo.add("Q881875,\"\"\"\"N18\"");
			toDo.add("Q151993,\"\"\"\"N20\"");
			toDo.add("Q153464,\"\"\"\"N22\"");
			toDo.add("Q445609,\"\"\"\"N24\"");
			toDo.add("Q152356,\"\"\"\"N28\"");
			toDo.add("Q1380992,\"\"\"\"N32\"");
			toDo.add("Q1381014,\"\"\"\"N33\"");
			toDo.add("Q1413205,\"\"\"\"N34\"");
			toDo.add("Q7904317,\"\"\"\"N42\"");
			toDo.add("Q836937,\"\"\"\"N44\"");
			toDo.add("Q641138,\"\"\"\"N45\"");
			toDo.add("Q249428,\"\"\"\"N46\"");
			toDo.add("Q152420,\"\"\"\"N47\"");
			toDo.add("Q708742,\"\"\"\"N48\"");
			toDo.add("Q698162,\"\"\"\"N57\"");
			toDo.add("Q657241,\"\"\"\"N62\"");
			toDo.add("Q649192,\"\"\"\"N63\"");
			toDo.add("Q650645,\"\"\"\"N64\"");
			toDo.add("Q697254,\"\"\"\"N65\"");
			toDo.add("Q514557,\"\"\"\"N66\"");
			toDo.add("Q700198,\"\"\"\"N68\"");
			toDo.add("Q573290,\"\"\"\"N69\"");
			toDo.add("Q835382,\"\"\"\"N70\"");
			toDo.add("Q153943,\"\"\"\"N76\"");
			toDo.add("Q829718,\"\"\"\"N77\"");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write a SKOS turtle file for nwbib-spatial to the conf/ folder
	 *
	 * @param args Not used
	 */
	public static void main(String[] args) {
		AtomicInteger exitCode = new AtomicInteger(-1);
		running(testServer(3333), () -> {
			initCsv();
			Model model = ModelFactory.createDefaultModel();
			setUpNamespaces(model);
			Pair<List<JsonNode>, Map<String, List<JsonNode>>> topAndSub =
					Classification.Type.from("Raumsystematik").buildHierarchy();
			Resource scheme = addConceptScheme(model);
			addTopLevelConcepts(model, topAndSub.getLeft(), scheme);
			addHierarchy(model, topAndSub.getRight());
			write(model);
			try (
					PrintWriter pw1 =
							new PrintWriter(new File("conf/qid-p6814-missing-in-wiki.csv"),
									StandardCharsets.UTF_8.name());
					PrintWriter pw2 =
							new PrintWriter(new File("conf/qid-p6814-missing-in-nwbib.csv"),
									StandardCharsets.UTF_8.name());
					PrintWriter pw3 =
							new PrintWriter(new File("conf/qid-p6814-qal4900-single.csv"),
									StandardCharsets.UTF_8.name());
					PrintWriter pw4 =
							new PrintWriter(new File("conf/qid-p6814-qal4900-multi.csv"),
									StandardCharsets.UTF_8.name())) {
				ArrayList<String> toDoCopy = new ArrayList<>(toDo);
				ArrayList<String> doneCopy = new ArrayList<>(done);
				toDoCopy.removeAll(done);
				doneCopy.removeAll(toDo);
				doneCopy.removeAll(zeroHits(doneCopy));
				pw1.println("qid,P6814");
				pw2.println("qid,P6814");
				pw3.println("qid,P6814,qal4900");
				pw4.println("qid,P6814,qal4900");
				toDoCopy.forEach(pw1::println);
				doneCopy.forEach(pw2::println);
				Pair<Iterable<String>, Iterable<String>> singleAndMulti =
						qalLines(model);
				singleAndMulti.getLeft().forEach(pw3::println);
				singleAndMulti.getRight().forEach(pw4::println);
				exitCode.set(toDoCopy.size() == 0 && doneCopy.size() == 0 ? 0 : -1);
			} catch (FileNotFoundException | UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		});
		System.exit(exitCode.get());
	}

	private static Pair<Iterable<String>, Iterable<String>> qalLines(
			Model model) {
		List<String> single = new ArrayList<>();
		List<String> multi = new ArrayList<>();
		StmtIterator broaderStatements =
				model.listStatements((Resource) null, SKOS.broader, (RDFNode) null);
		broaderStatements.forEachRemaining(broaderStatement -> {
			String subId = broaderStatement.getSubject().getURI()
					.substring("https://nwbib.de/spatial#".length());
			List<Statement> focusStatements =
					model.listStatements(broaderStatement.getObject().asResource(),
							FOAF.focus, (RDFNode) null).toList();
			for (Statement focusStatement : focusStatements) {
				String focusOfTop = focusStatement.getObject().toString()
						.substring("http://www.wikidata.org/entity/".length());
				String line =
						String.format("%s,\"\"\"\"%s\",%s", subId, subId, focusOfTop);
				int broaderCount = model.listStatements(broaderStatement.getSubject(),
						SKOS.broader, (RDFNode) null).toList().size();
				(broaderCount == 1 ? single : multi).add(line);
			}
		});
		Collections.sort(multi);
		return Pair.of(single, multi);
	}

	private static List<String> zeroHits(List<String> list) {
		return list.stream()
				.filter((String line) -> Lobid
						.getTotalHits("spatial.id",
								"\"https://nwbib.de/spatial#" + line.split(",")[0] + "\"", "")
						.get(Lobid.API_TIMEOUT).equals(0L))
				.collect(Collectors.toList());
	}

	private static void setUpNamespaces(Model model) {
		model.setNsPrefix("skos", SKOS.NAMESPACE.toString());
		model.setNsPrefix("foaf", FOAF.NAMESPACE.toString());
		model.setNsPrefix("dct", DCTerms.NAMESPACE.toString());
		model.setNsPrefix("vann", "http://purl.org/vocab/vann/");
		model.setNsPrefix("nwbib-spatial", NWBIB_SPATIAL_NAMESPACE);
		model.setNsPrefix("wd", "http://www.wikidata.org/entity/");
	}

	private static Resource addConceptScheme(Model model) {
		return model.createResource(NWBIB_SPATIAL, SKOS.ConceptScheme)//
				.addProperty(DCTerms.title,
						"Raumsystematik der Nordrhein-Westfälischen Bibliographie", "de")
				.addProperty(DCTerms.title,
						"Spatial classification scheme of the North Rhine-Westphalian bibliography",
						"en")
				.addProperty(DCTerms.license,
						model.createResource(
								"http://creativecommons.org/publicdomain/zero/1.0/"))
				.addProperty(DCTerms.description,
						"This controlled vocabulary for areas in Northrhine-Westphalia was created for use in the North Rhine-Westphalian bibliography. The initial transformation to SKOS was carried out by Felix Ostrowski for the hbz.", "en")
				.addProperty(DCTerms.issued, "2014-01-28")
				.addProperty(DCTerms.modified,
						ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
				.addProperty(DCTerms.publisher,
						model.createResource("http://lobid.org/organisations/DE-605"))
				.addProperty(
						model.createProperty(
								"http://purl.org/vocab/vann/preferredNamespaceUri"),
						NWBIB_SPATIAL_NAMESPACE)
				.addProperty(
						model.createProperty(
								"http://purl.org/vocab/vann/preferredNamespacePrefix"),
						"nwbib-spatial");
	}

	private static void addTopLevelConcepts(Model model, List<JsonNode> topLevel,
			Resource scheme) {
		topLevel.forEach(top -> {
			addInSchemePrefLabelAndNotation(model, top);
			scheme.addProperty(SKOS.hasTopConcept,
					model.createResource(top.get("value").asText()));
		});
	}

	private static void addHierarchy(Model model,
			Map<String, List<JsonNode>> hierarchy) {
		hierarchy.entrySet().forEach(sub -> {
			sub.getValue().forEach(entry -> {
				try {
					String superSubject = sub.getKey();
					Resource resource = addInSchemePrefLabelAndNotation(model, entry)
							.addProperty(SKOS.broader, model.createResource(superSubject));
					addFocus(model, entry, resource);
				} catch (Exception e) {
					System.err.println("Error processing: " + entry);
					e.printStackTrace();
				}
			});
		});
	}

	private static void addFocus(Model model, JsonNode entry, Resource resource) {
		String focus;
		// use original focus info from old SKOS file:
		if (entry.has("focus")
				&& !(focus = entry.get("focus").asText().trim()).isEmpty()) {
			resource.addProperty(FOAF.focus, model.createResource(focus));
		}
		// spatial QIDs focus on their Wikidata equivalent:
		else {
			String value = entry.get("value").asText().trim();
			if (Lobid.isWikidata(value) && !(focus = value).isEmpty()) {
				resource.addProperty(FOAF.focus,
						model.createResource(focus.replace(NWBIB_SPATIAL_NAMESPACE,
								"http://www.wikidata.org/entity/")));
			}
		}
	}

	private static Resource addInSchemePrefLabelAndNotation(Model model,
			JsonNode top) {
		String subject = top.get("value").asText();
		String nwbibId =
				top.has("nwbibId") ? top.get("nwbibId").asText() : lastSegment(subject);
		String label = top.get("label").asText()
				.replaceAll("<span class='notation'>([^<]*)</span>", "").trim();
		boolean wiki = Lobid.isWikidata(subject);
		String wikidataId =
				lastSegment(top.has("focus") ? top.get("focus").asText() : subject);
		String id = top.has("nwbibId") ? nwbibId : wikidataId;
		if (id.equalsIgnoreCase("n35") || id.equalsIgnoreCase("n37"))
			throw new IllegalArgumentException(
					"Skipping n35/n37 (temp. workaround, expected)");
		JsonNode notation = top.get("notation");
		if (wiki) {
			toDo.add(wikidataId + ",\"\"\"\"" + nwbibId + "\"");
		}
		Resource result = model
				.createResource(wiki ? NWBIB_SPATIAL_NAMESPACE + id : subject,
						SKOS.Concept)//
				.addProperty(SKOS.inScheme, //
						model.createResource(NWBIB_SPATIAL))
				.addProperty(SKOS.prefLabel, label, "de");
		if (notation != null && !notation.asText().isEmpty()) {
			result.addProperty(SKOS.notation, notation.asText());
		}
		return result;
	}

	private static String lastSegment(String uri) {
		String[] segments = uri.split("[#/]");
		return segments[segments.length - 1].trim();
	}

	private static void write(Model model) {
		model.write(System.out, Lang.TURTLE.getName());
		try (FileWriter fw = new FileWriter("conf/nwbib-spatial.ttl")) {
			model.write(fw, Lang.TURTLE.getName());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
