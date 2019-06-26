
/* Copyright 2019 Fabian Steeg, hbz. Licensed under the GPLv2 */

import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.FileWriter;
import java.io.IOException;
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
