# Welche erweiterten Suchen greifen auf welche MAB-/JSON-Felder zu?

Stand: 8.3.2018 [test.nwbib.de](test.nwbib.de)

Zur Erklärung: 
- Alles was `markiert` ist, findet sich in der MAB- bzw. JSON-Datei.
- Alles, was in eckigen Klammern steht, ist eine Option. Z.B. wird mit `540[-12][-1].[ab]` nach den MAB-Feldern und Unterfeldern mit den ersten Indikatoren "leer", "1" oder "2", dem zweiten Indikator "leer" oder "1" und den Unterfeldern "a" oder "b" gesucht. Bei nummerierten Listen wird der erste zutreffende Eintrag genommen.

Bei den Einträgen zum MAB-Feld wird kurz beschrieben, wie diese in das JSON-Feld umgewandelt werden. Punkte innerhalb der JSON- oder MAB-Notationen zeigen eine tiefere Ebene an, z.B. zeigt `publication.startDate` an, dass die Eigenschaft `startDate` in `publication` enthalten ist.

Als Beispiele werden die JSON-Dateien, MAB-XML-Dateien und NWBib-URLs verlinkt.

## Alle Wörter

Suche über alle erweiterten Suchen, zu Details siehe jeweilige Beschreibung unten.

## ISBN/ISSN

##### JSON

* `isbn`, `issn`, `hbzId`, zu Details siehe `IdQuery` in [Queries.java](https://github.com/hbz/lobid-resources/blob/master/web/app/controllers/resources/Queries.java)

##### MAB

* `540[-ab][-1].[ab]` (für ISBN) bzw. `542[-ab][-1].a` (für ISSN). Bei ISBNs werden die Bindestriche weggenommen.

##### Beispiele

* ISBN: [BT000004645 (JSON)](http://lobid.org/resources/BT000004645.json) / [BT000004645 (MAB-XML)](http://lobid.org/hbz01/BT000004645) / [BT000004645 (NWBib)](https://nwbib.de/BT000004645)
* ISSN: [HT002215064 (JSON)](http://lobid.org/resources/HT002215064.json) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)

## Titel

##### JSON

* `title`, `otherTitleInformation`, zu Details siehe `NameQuery` in [Queries.java](https://github.com/hbz/lobid-resources/blob/master/web/app/controllers/resources/Queries.java)

##### MAB

1. Zusammengesetzter Titel aus dem Titel der Überordnung (`331[-ab]2.a`), Bandangabe (`090-[-1].a` oder wenn nicht vorhanden, `089-[-1].a`) und dem Titel des Werks (`331[-ab][-1].a`).
2. `310[-ab][-12].a`
3. `331[-ab][-1].a`
4. `333[-ab][-1].a`

##### Beispiel

* [HT002215064 (JSON)](http://lobid.org/resources/HT002215064.json) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)

## Person

##### JSON

* `contribution.agent.label`, `contribution.agent.altLabel`, `contribution.agent.id`
* wenn `contribution.agent.type` = `Person`
* zu Details siehe `nestedContribution` in [Lobid.java](https://github.com/hbz/nwbib/blob/master/app/controllers/nwbib/Lobid.java)

##### MAB

* Jedes vierte 100er-Feld mit `[-abcefmn][12].[pa]`.

##### Beispiele

* [HT019248992 (JSON)](http://lobid.org/resources/HT019248992.json) / [HT019248992 (MAB-XML)](http://lobid.org/hbz01/HT019248992) / [HT019248992 (NWBib)](https://nwbib.de/HT019248992)

## Körperschaft

##### JSON

* `contribution.agent.label`, `contribution.agent.altLabel`, `contribution.agent.id`
* wenn `contribution.agent.type` = `CorporateBody`
* zu Details siehe `nestedContribution` in [Lobid.java](https://github.com/hbz/nwbib/blob/master/app/controllers/nwbib/Lobid.java)

##### MAB 

* Konferenzen und Ereignisse: Zusammengesetzt aus Ereignis (200er-Feld mit `[-abcfep][12].e`) und wenn vorhanden, Zählung (Unterfeld `n`), Datum (Unterfeld `d`) und Körperschaftsschlagwort (Ansetzung unter dem Ortssitz; Unterfeld `c`).
* Erschaffende Körperschaften: Zusammengesetzt aus Körperschaft (ein 200er-Feld mit `[-a][12].k`) und wenn vorhanden mit Unterfeld `h`, `b` oder beidem.
* Beitragende Körperschaften: Wie bei den erschaffenden Körperschaften nur mit dem ersten Indikator `b`, `c`, `e`, `f` oder `p`.
	* Verbindungen von geografischen Einheiten bzw. Körperschaften mit hierarchischer Information. Z.B. in [BT000002852](http://lobid.org/hbz01/BT000002852).

##### Beispiele

* [HT002215064 (JSON)](http://lobid.org/resources/HT002215064.json) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)

## Schlagwort

##### JSON

* `subject.componentList.label`, `subject.componentList.id`, `subjectAltLabel`
* zu Details siehe `SubjectQuery` in [Queries.java](https://github.com/hbz/lobid-resources/blob/master/web/app/controllers/resources/Queries.java)

##### MAB

* Formschlagwörter: `9[01234][27]-[-12].f`. Z.B. in [HT002215064](http://lobid.org/hbz01/HT002215064) ("Zeitschrift").
* Sachschlagwörter: `9[01234][27]-[-12].s`. Z.B. in [BT000004645](http://lobid.org/hbz01/BT000004645) ("Führer").
* Personen, Körperschaften, Ereignisse, Konferenzen, Orte wird ähnlich wie im Abschnitt "Person" bzw. "Körperschaft" erstellt.
* Werke: Besteht `9[01234][27]-[-12].t` wird es teilweise mit dem Autoren verbunden. Z.B. in [HT017034736](http://lobid.org/hbz01/HT017034736) ("Viebig, Clara: Das Kreuz im Venn").
* Freie Schlagwörter: `710[-abcdfz][123].a`. Z.B. in [HT006934472](http://lobid.org/hbz01/HT006934472) ("Düsseldorf").
* `subjectAltLabel`: MAB `[56][27]` sind Verweisungsformen auf die Schlagwörter aus MAB 902-947.
	1. Wenn `9[56][27]-[12].[acefgkps]` vorhanden ist, verbinde es mit `9[56][27]-[12].b` oder `9[56][27]-[12].x` oder `9[56][27]-[12].[cdhmortuz]`.
	2. `9[56][27]-[12].[acefgkps]`

##### Beispiele

* [HT019248992 (JSON)](http://lobid.org/resources/HT019248992.json) / [HT019248992 (MAB-XML)](http://lobid.org/hbz01/HT019248992) / [HT019248992 (NWBib)](https://nwbib.de/HT019248992)

## Verlag

* JSON: `publication.publishedBy`, zu Details siehe `PublisherQuery` in [Queries.java](https://github.com/hbz/lobid-resources/blob/master/web/app/controllers/resources/Queries.java)
* MAB: `41[27][-abcu][-12].[ag]` oder `419-[12].b`. Eckige Klammern und Zeichenketten, die nur `S.n.` bzw. `s.n.` enthalten, werden nicht berücksichtigt.
* Beispiel: [BT000004645 (JSON)](http://lobid.org/resources/BT000004645.json) / [BT000004645 (MAB-XML)](http://lobid.org/hbz01/BT000004645) / [BT000004645 (NWBib)](https://nwbib.de/BT000004645)

## Erscheinungsjahr

* JSON: `publication.startDate`, zu Details siehe `IssuedQuery` in [Queries.java](https://github.com/hbz/lobid-resources/blob/master/web/app/controllers/resources/Queries.java)
* MAB: `425[ab-p][-1].a` oder `419-1c` oder `595-[-12].a`, wenn die Jahreszahl 1000-2099 entspricht.
* Beispiel: [HT002215064 (JSON)](https://test.nwbib.de/HT002215064) / [HT002215064 (MAB-XML)](http://lobid.org/hbz01/HT002215064) / [HT002215064 (NWBib)](https://nwbib.de/HT002215064)
* Anmerkung: Die Suche nach Erscheinungsjahren findet sowohl Erstpublikationen als auch Digitalisierungen bzw. andere Sekundärpublikationen. 
