# GraphQl to ...
Converts a GrapQL schema to PlantUML

## How to run

```bash
$ java -jar gql-to.jar 
```
options:

| short | long | description |
| ----- | ---- | ----------- |
| -h | --help | Print usage.|
| -i | --input \<arg\> | input file/directory name
| -l | --language \<arg\> | Target language, supported languages: PLANTUML, TEXTUML, PROTO (future: XMI)
| -o | --output \<arg\> | Output file/directory name.
| -p | --package \<arg\> | UML package name
| -d | --diagram | Optional output as PNG or SVG
