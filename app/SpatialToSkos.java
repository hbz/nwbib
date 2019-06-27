
/* Copyright 2019 Fabian Steeg, hbz. Licensed under the GPLv2 */

import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SKOS;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Classification;
import controllers.nwbib.Lobid;

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

	// Temporary: also write a CSV file for Wikidata batch import,
	// see https://github.com/hbz/nwbib/issues/469
	static PrintWriter pw;
	static {
		try {
			pw = new PrintWriter(new File("conf/qid-p6814.csv"),
					StandardCharsets.UTF_8.name());
			pw.println("qid,P6814");
			pw.println("Q1198,\"\"\"\"N01\"");
			pw.println("Q152243,\"\"\"\"N03\"");
			pw.println("Q8614,\"\"\"\"N04\"");
			pw.println("Q462011,\"\"\"\"N10\"");
			pw.println("Q72931,\"\"\"\"N12\"");
			pw.println("Q2036208,\"\"\"\"N13\"");
			pw.println("Q4194,\"\"\"\"N14\"");
			pw.println("Q580471,\"\"\"\"N16\"");
			pw.println("Q881875,\"\"\"\"N18\"");
			pw.println("Q151993,\"\"\"\"N20\"");
			pw.println("Q153464,\"\"\"\"N22\"");
			pw.println("Q445609,\"\"\"\"N24\"");
			pw.println("Q152356,\"\"\"\"N28\"");
			pw.println("Q1380992,\"\"\"\"N32\"");
			pw.println("Q1381014,\"\"\"\"N33\"");
			pw.println("Q1413205,\"\"\"\"N34\"");
			pw.println("Q7904317,\"\"\"\"N42\"");
			pw.println("Q836937,\"\"\"\"N44\"");
			pw.println("Q641138,\"\"\"\"N45\"");
			pw.println("Q249428,\"\"\"\"N46\"");
			pw.println("Q152420,\"\"\"\"N47\"");
			pw.println("Q708742,\"\"\"\"N48\"");
			pw.println("Q698162,\"\"\"\"N57\"");
			pw.println("Q657241,\"\"\"\"N62\"");
			pw.println("Q649192,\"\"\"\"N63\"");
			pw.println("Q650645,\"\"\"\"N64\"");
			pw.println("Q697254,\"\"\"\"N65\"");
			pw.println("Q514557,\"\"\"\"N66\"");
			pw.println("Q700198,\"\"\"\"N68\"");
			pw.println("Q573290,\"\"\"\"N69\"");
			pw.println("Q835382,\"\"\"\"N70\"");
			pw.println("Q153943,\"\"\"\"N76\"");
			pw.println("Q829718,\"\"\"\"N77\"");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write a SKOS turtle file for nwbib-spatial to the conf/ folder
	 * 
	 * @param args Not used
	 */
	public static void main(String[] args) {
		running(testServer(3333), () -> {
			Model model = ModelFactory.createDefaultModel();
			setUpNamespaces(model);
			Pair<List<JsonNode>, Map<String, List<JsonNode>>> topAndSub =
					Classification.Type.from("Raumsystematik").buildHierarchy();
			Resource scheme = addConceptScheme(model);
			addTopLevelConcepts(model, topAndSub.getLeft(), scheme);
			addHierarchy(model, topAndSub.getRight());
			write(model);
			pw.close();
		});
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
						"This controlled vocabulary for areas in Northrhine-Westphalia was created for use in the North Rhine-Westphalian bibliography. The initial transformation to SKOS was carried out by Felix Ostrowski for the hbz.")
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
					if (Lobid.isWikidata(superSubject)) {
						resource.addProperty(FOAF.focus,
								model.createResource(entry.get("value").asText()));
					}
				} catch (Exception e) {
					System.err.println("Error processing: " + entry);
					e.printStackTrace();
				}
			});
		});
	}

	private static Resource addInSchemePrefLabelAndNotation(Model model,
			JsonNode top) {
		String subject = top.get("value").asText();
		String label = top.get("label").asText();
		boolean wiki = Lobid.isWikidata(subject);
		String notation = subject.split("#")[1].trim();
		if (wiki) {
			pw.println(notation + ",\"\"\"\"" + notation + "\"");
		}
		return model
				.createResource(wiki ? NWBIB_SPATIAL_NAMESPACE + notation : subject,
						SKOS.Concept)//
				.addProperty(SKOS.inScheme, //
						model.createResource(NWBIB_SPATIAL))
				.addProperty(SKOS.prefLabel, label, "de")//
				.addProperty(SKOS.notation, notation);
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
