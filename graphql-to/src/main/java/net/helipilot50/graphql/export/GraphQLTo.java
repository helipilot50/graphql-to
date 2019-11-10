package net.helipilot50.graphql.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.log4j.Logger;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import edu.emory.mathcs.backport.java.util.Collections;
import net.helipilot50.graphql.export.grammar.GraphQLBaseListener;
import net.helipilot50.graphql.export.grammar.GraphQLLexer;
import net.helipilot50.graphql.export.grammar.GraphQLParser;
import net.helipilot50.graphql.export.grammar.GraphQLParser.ArgumentsDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.DefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.DirectiveContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.DocumentContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.EnumTypeDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.EnumValueContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.EnumValueDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.FieldDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.InputObjectTypeDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.InputValueDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.InterfaceTypeDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.ListTypeContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.NameContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.NonNullTypeContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.ObjectTypeDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.ScalarTypeDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.TypeContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.TypeDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.TypeNameContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.TypeSystemDefinitionContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.UnionMembersContext;
import net.helipilot50.graphql.export.grammar.GraphQLParser.UnionTypeDefinitionContext;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

enum Language {
	PLANTUML, TEXTUML, PROTO, XMI
}

enum ImageType {
	NONE,
	PNG,
	SVG
}

enum FieldType {
	INPUT, OUTPUT
}


public class GraphQLTo extends GraphQLBaseListener {
	private STGroup templates;
	private ParseTreeProperty<ST> code = new ParseTreeProperty<ST>();
	private GraphQLParser parser;
	private ParseTreeWalker walker = new ParseTreeWalker();
	private Set<String> reservedWords = new HashSet<String>();

	private List<ST> linkFields = new ArrayList<ST>();
	private List<ST> directiveConnections = new ArrayList<ST>();
	private List<ProtoLink> protoLinkFields = new ArrayList<ProtoLink>();
	private List<ST> scalarTypes = new ArrayList<ST>();
	private List<ST> enumSTList = new ArrayList<ST>();
	private ST currentType = null;
	private String currentField = null;
	private Set<String> systemTypes = new HashSet<String>();
	private Set<String> typeList = new HashSet<String>();
	private Set<String> inputList = new HashSet<String>();
	private Set<String> enumList = new HashSet<String>();
	private Set<String> scalarList = new HashSet<String>();

	Language language = null;
	private String packageName;
	// private String templateFileName;
	private String outputFileName;
	private ImageType imageType;

	private static Logger log = Logger.getLogger(GraphQLTo.class);

	private class ProtoLink {
		public ST currentType;
		public ST nameST;
		public String typeString;
		public ST cardA;
		public ST cardB;

		ProtoLink(ST currentType, ST nameST, String typeString, ST cardA, ST cardB){
			this.currentType = currentType;
			this.nameST = nameST;
			this.typeString = typeString;
			this.cardA = cardA;
			this.cardB = cardB;
		}
	}

	public GraphQLTo() {
		super();
		systemTypes.add("int");
		systemTypes.add("float");
		systemTypes.add("string");
		systemTypes.add("boolean");
		systemTypes.add("id");
		// custom scalars
		//		systemTypes.add("BigDecimal".toLowerCase());
		//		systemTypes.add("CurrencyCode".toLowerCase());
		//		systemTypes.add("Date".toLowerCase());
		//		systemTypes.add("EmailAddress".toLowerCase());
		//		systemTypes.add("JSONType".toLowerCase());
		//		systemTypes.add("NonNegativeFloat".toLowerCase());
		//		systemTypes.add("ObjectId".toLowerCase());
		//		systemTypes.add("Sort".toLowerCase());
		//		systemTypes.add("UUID".toLowerCase());
		this.imageType = ImageType.NONE;

	}
	public void generate(String inputFileName, String outputFileName, Language language, String packageName) throws IOException {
		generate(inputFileName, outputFileName, language, packageName, ImageType.NONE);
	}

	public void generate(String inputFileName, String outputFileName, Language language, String packageName, ImageType imageType)
			throws IOException {
		// this.templateFileName = templateFileName;
		this.imageType = imageType;
		this.outputFileName = outputFileName;
		this.language = language;
		this.packageName = packageName;
		log.debug("Converting GraphQL file: " + inputFileName + " to " + outputFileName);
		File inputFile = new File(inputFileName);
		File outputFile = new File(outputFileName);
		if (inputFile.isDirectory()) {
			final String outputFileExtension = getTemplateFor("outputFileExtenstion").render();
			final Path outputPath = outputFile.toPath();
			final Path inputPath = inputFile.toPath();
			Files.walk(inputPath).filter(path -> !Files.isDirectory(path))
			.filter(path -> path.getFileName().toString().endsWith(".gql")).forEach(path -> {
				try {

					String s1 = path.toString();
					String s2 = inputPath.toString();
					String s3 = s1.substring(s2.length());
					String s4 = s3.substring(0, s3.lastIndexOf('.') + 1);
					String s5 = s4.concat(outputFileExtension);
					Path actualOutputPath = Paths.get(outputPath.toString(), s5);
					log.debug(path.toString() + " ==> " + actualOutputPath.toString());
					Files.createDirectories(actualOutputPath.getParent());

					generate(path, actualOutputPath);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		} else {
			generate(inputFile.toPath(), outputFile.toPath());
		}

	}

	private void loadReservedWords() {
		reservedWords.clear();
		ST st = getTemplateFor("reservedWordList");
		String reserverWordsString = st.render();
		String[] wordArray = reserverWordsString.split("\n");
		Collections.addAll(reservedWords, wordArray);
	}

	private String generate(Path input, Path output) throws IOException{
		org.antlr.v4.runtime.CharStream stream = CharStreams.fromFileName(input.toString());
		GraphQLLexer lexer = new GraphQLLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		String umlCode = generate(tokens);
		Files.write(output, umlCode.getBytes());
		if (this.imageType != ImageType.NONE) {
			String imageFileName = output.toString();
			imageFileName = imageFileName.substring(0, imageFileName.lastIndexOf('.'));

			SourceStringReader umlReader = new SourceStringReader(umlCode);
			FileOutputStream outputStream = null;
			String desc = null;
			switch (this.imageType) {
			case PNG:
				outputStream = new FileOutputStream(imageFileName + ".png");
				desc = umlReader.generateImage(outputStream, new FileFormatOption(FileFormat.PNG));
				break;
			case SVG:
				outputStream = new FileOutputStream(imageFileName + ".svg");
				desc = umlReader.generateImage(outputStream, new FileFormatOption(FileFormat.SVG));
				break;
			default:
				break;
			}
			outputStream.close();
		}

		return umlCode;
	}

	private String generate(CommonTokenStream tokens) {
		switch (language) {
		case PLANTUML: {
			templates = new STGroupFile(getClass().getResource("/plantuml.stg"), null, '$', '$');
			break;
		}
		case TEXTUML: {
			templates = new STGroupFile(getClass().getResource("/textuml.stg"), null, '$', '$');
			break;
		}
		case PROTO: {
			templates = new STGroupFile(getClass().getResource("/protobuf.stg"), null, '$', '$');
			break;
		}
		default:
			templates = new STGroupFile(getClass().getResource("/plantuml.stg"), null, '$', '$');
			break;
		}
		loadReservedWords();
		parser = new GraphQLParser(tokens);
		ParseTree tree = parser.document();
		walker.walk(this, tree);
		String exportText = code.get(tree).render();
		log.debug("Exported:\n" + exportText);
		return exportText;
	}

	private ST getTemplateFor(String name) {
		ST st = templates.getInstanceOf(name);
		return st;
	}

	private void putCode(ParseTree ctx, ST st) {
		if (st != null)
			log.trace("Rendered: " + st.render());
		code.put(ctx, st);
	}

	private boolean isSystemType(String typeName) {
		return systemTypes.contains(typeName.toLowerCase());
	}
	private boolean isEnumType(String typeName) {
		return this.enumList.contains(typeName);
	}
	@Override
	public void exitDocument(DocumentContext ctx) {
		ST st = getTemplateFor("exitDocument");
		if (ctx.definition() != null) {
			for (ParseTree def : ctx.definition()) {
				ST st2 = code.get(def);
				if (st2 != null) {
					st.add("definitions", st2);
				}

			}
		}
		
		linkFields(st, protoLinkFields);

		for (ST scalarST: this.scalarTypes) {
			st.add("scalars", scalarST);
		}

		for (ST enumST: this.enumSTList) {
			st.add("enums", enumST);
		}
		for (ST directiveST: this.directiveConnections) {
			st.add("directives", directiveST);
		}
		st.add("package", this.packageName);
		putCode(ctx, st);
		
		log.info(printElementCollection(this.typeList, "Type"));
		log.info(printElementCollection(this.inputList, "Input"));
		log.info(printElementCollection(this.enumList, "Enum"));
		log.info(printElementCollection(this.scalarList, "Scalar"));
	}

	private String printElementCollection(Set<String> collection, String type) {
		// TODO make this into a template
		StringBuilder sb = new StringBuilder("||" +type +" name||Issues||Fix||\n");
		for (String element : collection) {
			sb.append('|');
			sb.append(element);
			sb.append("| | |\n");
		}
		sb.append("|total: ");
		sb.append(collection.size());
		sb.append('|');
		return sb.toString();
	}
	
	@Override
	public void exitDefinition(DefinitionContext ctx) {
		putCode(ctx, code.get(ctx.getChild(0)));
	}

	@Override
	public void exitTypeSystemDefinition(TypeSystemDefinitionContext ctx) {
		putCode(ctx, code.get(ctx.getChild(0)));
	}

	@Override
	public void exitTypeDefinition(TypeDefinitionContext ctx) {
		putCode(ctx, code.get(ctx.getChild(0)));
	}

	@Override
	public void exitUnionTypeDefinition(UnionTypeDefinitionContext ctx) {
		ST st = getTemplateFor("unionTypeDefinition");
		st.add("name", code.get(ctx.name()));
		UnionMembersContext members = ctx.unionMembers();
		traverseUnionMembers(st, members);
		putCode(ctx, st);
	}

	private void traverseUnionMembers(ST unionST, UnionMembersContext ctx) {
		if (ctx.typeName() != null)
			unionST.add("members", code.get(ctx.typeName().name()));

		if (ctx.unionMembers() != null)
			traverseUnionMembers(unionST, ctx.unionMembers());
	}

	@Override
	public void exitUnionMembers(UnionMembersContext ctx) {
		putCode(ctx, code.get(ctx.typeName()));
	}

	@Override
	public void exitInterfaceTypeDefinition(InterfaceTypeDefinitionContext ctx) {
		ST st = getTemplateFor("interfaceTypeDefinition");
		st.add("name", code.get(ctx.name()));
		if (ctx.fieldDefinition() != null) {
			for (ParseTree field : ctx.fieldDefinition()) {
				st.add("fields", code.get(field));
			}
		}
		putCode(ctx, st);
	}

	@Override
	public void enterObjectTypeDefinition(ObjectTypeDefinitionContext ctx) {
		currentType = templateForSystemType(ctx.name().getText());
		super.enterObjectTypeDefinition(ctx);
	}

	@Override
	public void exitObjectTypeDefinition(ObjectTypeDefinitionContext ctx) {
		ST st = getTemplateFor("objectTypeDefinition");
		this.typeList.add(ctx.name().getText());
		ST typeName = code.get(ctx.name());
		st.add("name", typeName);
		if (ctx.implementsInterfaces() != null) {
			for (TypeNameContext type : ctx.implementsInterfaces().typeName())
				st.add("interfaces", type.getText());
		}
		if (ctx.fieldDefinition() != null) {
			for (ParseTree field : ctx.fieldDefinition()) {
				st.add("fields", code.get(field));
			}
		}
		putCode(ctx, st);
	}


	private void linkFields(ST st, List<ProtoLink> protoLinkFields) {
		for (ProtoLink lf: protoLinkFields) {
			System.out.println(String.format("isEnumType(%s) =  %b", lf.typeString, isEnumType(lf.typeString)));
			if (!isSystemType(lf.typeString) && !isEnumType(lf.typeString)) {
				ST linkST = linkFieldST(lf.currentType, lf.nameST, lf.typeString, lf.cardA, lf.cardB);
				st.add("linkFields", linkST);
			}
		}
	}

	private ST linkFieldST(ST typeName, ST fieldName, String methodType, ST sourceCard, ST destCard) {
		ST associationST = getTemplateFor("association");
		associationST.add("typeA", typeName);
		associationST.add("nameA", fieldName);
		associationST.add("cardA", sourceCard);

		String backwardName = typeName.render().toLowerCase();
		associationST.add("nameB", templateForReservedWord(backwardName));
		associationST.add("typeB", templateForReservedWord(methodType));
		associationST.add("cardB", destCard);

		return associationST;
	}

	@Override
	public void enterInputObjectTypeDefinition(InputObjectTypeDefinitionContext ctx) {
		//		linkFields.clear();
		currentType = templateForSystemType(ctx.name().getText());
		//		System.out.println(currentType.render());
		super.enterInputObjectTypeDefinition(ctx);
	}

	@Override
	public void exitInputObjectTypeDefinition(InputObjectTypeDefinitionContext ctx) {
		ST st = getTemplateFor("inputObjectTypeDefinition");
		this.inputList.add(ctx.name().getText());
		ST typeName = code.get(ctx.name());
		st.add("name", typeName);
		if (ctx.inputValueDefinition() != null) {
			for (ParseTree inputValue : ctx.inputValueDefinition()) {
				st.add("inputValues", code.get(inputValue));
			}
		}
		putCode(ctx, st);
	}

	@Override
	public void enterFieldDefinition(FieldDefinitionContext ctx) {
		currentField  = ctx.name().getText();
		super.enterFieldDefinition(ctx);
	}
	@Override
	public void exitFieldDefinition(FieldDefinitionContext ctx) {
		//		String name = ctx.name().getText();
		TypeContext typeCtx = ctx.type();
		ST fieldST = fieldDefinition(ctx.name(), typeCtx, FieldType.OUTPUT);
		if (ctx.argumentsDefinition() != null) {
			for (InputValueDefinitionContext argDef : ctx.argumentsDefinition().inputValueDefinition()) {
				fieldST.add("arguments", code.get(argDef));
			}
		}
		putCode(ctx, fieldST);
	}

	@Override
	public void exitInputValueDefinition(InputValueDefinitionContext ctx) {
		TypeContext typeCtx = ctx.type();
		ST st = fieldDefinition(ctx.name(), typeCtx, FieldType.INPUT);
		putCode(ctx, st);
	}

	private ST fieldDefinition(NameContext name, TypeContext typeCtx, FieldType type) {
		String typeName = typeNameFromContext(typeCtx);
		boolean systemType = isSystemType(typeName);
		ST nameST = code.get(name);
		ST st = null;
		switch (type) {
		case INPUT:
			st = getTemplateFor("inputValueDefinition");
			break;
		default:
			st = getTemplateFor("operation");
			break;
		}
		st.add("name", nameST);
		if (systemType && (typeCtx.nonNullType() != null) && (typeCtx.nonNullType().listType() != null))
			st.add("type", code.get(typeCtx.nonNullType().listType()));
		else if ((typeCtx.nonNullType() != null) && (typeCtx.nonNullType().listType() != null))
			st.add("type", code.get(typeCtx.nonNullType().listType()));
		else if (typeCtx.listType() != null)
			st.add("type", code.get(typeCtx.listType()));
		else if (typeCtx.nonNullType() != null)
			st.add("type", code.get(typeCtx.nonNullType().typeName()));
		else
			st.add("type", code.get(typeCtx));

		if (!systemType && (currentType != null)) {
			// association
			createAssociation(linkFields, nameST, typeCtx);
		}

		return st;
	}

	private void createAssociation(List<ST> linkFields, ST nameST, TypeContext typeCtx) {
		// association
		ST cardB = null;
		String typeString = null;
		if (typeCtx.nonNullType() != null) {
			if (typeCtx.nonNullType().listType() != null) {
				cardB = getTemplateFor("oneToMany");
				typeString = code.get(typeCtx.nonNullType().listType().type()).render();
			} else {
				cardB = getTemplateFor("exactlyOne");
				// ST typeST = getTemplateFor("listType");
				typeString = code.get(typeCtx.nonNullType().typeName()).render();
			}
		} else if (typeCtx.listType() != null) {
			cardB = getTemplateFor("zeroToMany");
			ST typeST = getTemplateFor("listType");
			typeST.add("typeName", code.get(typeCtx.listType().type()));
			typeString = typeST.render();
		} else {
			cardB = getTemplateFor("zeroOrOne");
			typeString = typeCtx.getText();
		}
		protoLinkFields.add(new ProtoLink(currentType, nameST, typeString, getTemplateFor("exactlyOne"), cardB));
		//		ST linkST = linkFieldST(currentType, nameST, typeString, getTemplateFor("exactlyOne"), cardB);
		//		linkFields.add(linkST);

	}

	@Override
	public void exitArgumentsDefinition(ArgumentsDefinitionContext ctx) {
		ST st = getTemplateFor("argumentsDefinition");
		if (ctx.inputValueDefinition() != null) {
			for (ParseTree inputValueDefinition : ctx.inputValueDefinition()) {
				st.add("arguments", code.get(inputValueDefinition));
			}
		}
		putCode(ctx, st);
	}

	private String typeNameFromContext(TypeContext typeCtx) {
		TypeContext targetType = typeFromContext(typeCtx);
		if (targetType.nonNullType() != null)
			return targetType.nonNullType().typeName().getText();
		else
			return targetType.typeName().getText();
	}

	private TypeContext typeFromContext(TypeContext typeCtx) {
		if (typeCtx.listType() != null)
			return typeCtx.listType().type();
		else if (typeCtx.nonNullType() != null) {
			if (typeCtx.nonNullType().listType() != null)
				return typeCtx.nonNullType().listType().type();
			else
				return typeCtx;
		} else
			return typeCtx;
	}

	@Override
	public void exitType(TypeContext ctx) {
		ST st = getTemplateFor("type");
		if (ctx.listType() != null)
			st.add("type", code.get(ctx.listType()));
		else if (ctx.nonNullType() != null) {
			NonNullTypeContext nonNullCtx = ctx.nonNullType();
			if (nonNullCtx.listType() == null) {
				st.add("type", code.get(nonNullCtx.typeName()));
			} else {
				st.add("type", code.get(nonNullCtx.listType()));
			}
		} else {
			st.add("type", code.get(ctx.typeName()));
		}
		putCode(ctx, st);
	}

	@Override
	public void exitTypeName(TypeNameContext ctx) {
		putCode(ctx, code.get(ctx.getChild(0)));
	}

	@Override
	public void exitListType(ListTypeContext ctx) {
		ST st = getTemplateFor("listType");
		if (ctx.type().nonNullType() != null) {
			ST oneToMany = getTemplateFor("oneToMany");
			st.add("cardinality", oneToMany);
		} else {
			ST many = getTemplateFor("zeroToMany");
			st.add("cardinality", many);
		}

		st.add("typeName", code.get(ctx.type()));
		putCode(ctx, st);
	}

	@Override
	public void exitNonNullType(NonNullTypeContext ctx) {
		ST st = getTemplateFor("nonNullType");
		st.add("name", ctx.typeName());
		putCode(ctx, st);
	}

	@Override
	public void exitEnumTypeDefinition(EnumTypeDefinitionContext ctx) {
		ST st = getTemplateFor("enumTypeDefinition");
		this.enumList.add(ctx.name().getText());
		st.add("name", code.get(ctx.name()));
		if (ctx.enumValueDefinition() != null) {
			for (ParseTree enumValue : ctx.enumValueDefinition()) {
				st.add("enumValues", code.get(enumValue));
			}
		}
		//putCode(ctx, st);
		this.enumSTList.add(st);
		
	}

	@Override
	public void exitEnumValueDefinition(EnumValueDefinitionContext ctx) {
		putCode(ctx, code.get(ctx.enumValue()));
	}

	@Override
	public void exitEnumValue(EnumValueContext ctx) {
		ST st = getTemplateFor("enumValue");
		st.add("value", code.get(ctx.name()));
		putCode(ctx, st);
	}

	@Override
	public void exitScalarTypeDefinition(ScalarTypeDefinitionContext ctx) {
		ST st = getTemplateFor("scalarTypeDefinition");
		this.scalarList.add(ctx.name().getText());
		st.add("name", code.get(ctx.name()));
		this.scalarTypes.add(st);
		//System.out.println(ctx.name().getText().toLowerCase());
		this.systemTypes.add(ctx.name().getText().toLowerCase());

	}

	@Override
	public void exitName(NameContext ctx) {
		String name = ctx.getText();
		ST stx = templateForReservedWord(name);
		putCode(ctx, stx);
	}

	@Override
	public void exitDirective(DirectiveContext ctx) {
		ST st = getTemplateFor("directive");
		st.add("name", ctx.name().getText());
		st.add("type",currentType);
		st.add("field", currentField);
		System.out.println(st.render());
		directiveConnections.add(st);
		
	}
	
	private ST templateForSystemType(String typeName) {
		ST st = null;
		if (isSystemType(typeName)) {
			String systemTypeName = typeName.toLowerCase();
			st = getTemplateFor(systemTypeName);
		} else {
			st = getTemplateFor("customType");
			// ST seperatorST = getTemplateFor("packageSeperator");
			// String seperator = seperatorST.render();
			// String[] nameParts = typeName.split(seperator + "+");
			// String name = nameParts[nameParts.length-1]; //last element is the name
			st.add("typeName", typeName);
			// if (nameParts.length >1){ //if there is a package
			// String[] packages = Arrays.copyOfRange(nameParts, 0, nameParts.length-2);
			// st.add("package", packages);
			// }
		}
		return st;
	}

	private ST templateForReservedWord(String word) {
		ST st = null;
		if (reservedWords.contains(word)) {
			st = getTemplateFor("reservedWord");
		} else {
			st = getTemplateFor("normalWord");
		}
		st.add("word", word);
		return st;
	}

}
