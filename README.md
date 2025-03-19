
```powershell
# git clone
mvn clean compile package -q -e
java -jar ./target/TreeTableTsvFx-0.1-shaded.jar --help
```
![treetabletsvfx2](https://github.com/user-attachments/assets/6e4d68ef-f2fc-45fd-b7c7-f38bdc65281b)

## Examples
Get children of Genghis Khan (bash):
```bash
read -r -d '' sparql <<'EOF'
PREFIX gas: <http://www.bigdata.com/rdf/gas#>

SELECT ?item ?itemLabel (SAMPLE(YEAR(?birthDate)) AS ?birthYear) (SAMPLE(YEAR(?deathDate)) AS ?deathYear) ?links ?parentId
WHERE {
  SERVICE gas:service {
    gas:program gas:gasClass "com.bigdata.rdf.graph.analytics.SSSP" ;
                gas:in wd:Q720 ;
                gas:traversalDirection "Forward" ;
                gas:out ?item ;
                gas:out1 ?depth ;
                gas:out2 ?parentId ;  # Capture the parent ID
                gas:maxIterations 4 ;
                gas:linkType wdt:P40 .
  }
  OPTIONAL { ?item wdt:P569 ?birthDate. }
  OPTIONAL { ?item wdt:P570 ?deathDate. }
  OPTIONAL { ?item wikibase:sitelinks ?links. }
  #OPTIONAL { ?item wdt:P40 ?linkTo }
  #OPTIONAL { ?item wdt:P18 ?pic }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "mul,en" }
}
GROUP BY ?item ?itemLabel ?links ?parentId
EOF

curl -X POST -H "Content-Type: application/sparql-query" -H "Accept: text/csv" --data "$sparql" https://query.wikidata.org/sparql | java -jar target/TreeTableTsvFx-0.1-shaded.jar , -
```
Create and open CSV:
```bash
read -r -d '' csv <<'EOF'
id,name,parentId
1,root,
2,child,1
3,alsoChild,1
4,grandChild,2
5,alsoGrandChild,2
6,anotherRootForNoReason,
7,childWithDanglingParentId,999
EOF
echo -e "$csv" > data.csv
java -jar target/TreeTableTsvFx-0.1-shaded.jar , data.csv
```
