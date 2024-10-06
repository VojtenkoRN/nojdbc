package nojdbc.core.builder;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import nojdbc.constant.BuilderConstant;
import nojdbc.core.annotation.result.Result;
import nojdbc.core.enumeration.ParamType;
import nojdbc.core.enumeration.RequestType;
import nojdbc.core.enumeration.ReturnType;
import nojdbc.core.pojo.Pair;
import nojdbc.core.pojo.RequestParam;
import nojdbc.core.pojo.ReturnParam;
import nojdbc.core.util.ResultSetUtil;
import nojdbc.core.util.TypeMirrorUtil;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

public final class DaoCodeBuilder {

    private static final int CLOSING_BRACKET_OFFSET = 4;
    private static final int MAX_MULTIPLIER_NUMBER_PARAMETERS = 1000;
    private final TypeMirrorUtil typeMirrorUtil;
    private final String methodReference;
    private String request = "";
    private List<RequestParam> params = Collections.emptyList();
    private ReturnParam returnParam = null;
    private Map<String, Result> results = Collections.emptyMap();

    private String connectionManagerName = "connectionManager";
    private String cacheName = "CACHE";
    private boolean fillSubfields = true;
    private boolean fieldFiller = false;

    private boolean isTransactional = true;
    private RequestType requestType = null;
    private boolean isBatch;

    private DaoCodeBuilder(TypeMirrorUtil typeMirrorUtil, String methodReference) {
        this.typeMirrorUtil = typeMirrorUtil;
        this.methodReference = methodReference;
    }

    public static DaoCodeBuilder init(TypeMirrorUtil util, String methodReference) {
        return new DaoCodeBuilder(util, methodReference);
    }

    public DaoCodeBuilder setRequest(String request) {
        this.request = request.trim().replaceAll("\"", "\\\\\"");
        return this;
    }

    public DaoCodeBuilder setParams(List<RequestParam> params) {
        this.params = params;
        return this;
    }

    public DaoCodeBuilder setReturnParam(ReturnParam returnParam) {
        this.returnParam = returnParam;
        return this;
    }

    public DaoCodeBuilder setResults(Map<String, Result> results) {
        this.results = results;
        return this;
    }

    public DaoCodeBuilder setConnectionManagerName(String connectionManagerName) {
        this.connectionManagerName = connectionManagerName;
        return this;
    }

    public DaoCodeBuilder setCacheName(String cacheName) {
        this.cacheName = cacheName;
        return this;
    }

    public DaoCodeBuilder setFillSubfields(boolean fillSubfields) {
        this.fillSubfields = fillSubfields;
        return this;
    }

    public DaoCodeBuilder setFieldFiller(boolean fieldFiller) {
        this.fieldFiller = fieldFiller;
        return this;
    }

    public DaoCodeBuilder setTransactional(boolean transactional) {
        isTransactional = transactional;
        return this;
    }

    public DaoCodeBuilder setRequestType(RequestType requestType) {
        this.requestType = requestType;
        return this;
    }

    /**
     * Построение блока кода из введенных ранее параметров.
     *
     * @return построенный блок кода.
     */
    public CodeBlock build() {
        if (request.isBlank() || isNull(returnParam)) {
            throw new RuntimeException("Request or ReturnParam required! [" + methodReference + "]");
        }
        final var codeBuilder = CodeBlock.builder();

        if (!params.isEmpty() && !ReturnType.VOID.equals(returnParam.returnType())) {
            final var earlyBreakIfCodeBlock = new StringBuilder("if (");
            params.forEach(requestParam -> earlyBreakIfCodeBlock.append("isNull(")
                  .append(requestParam.spec().name)
                  .append(") && "));
            earlyBreakIfCodeBlock.setLength(earlyBreakIfCodeBlock.length() - CLOSING_BRACKET_OFFSET);
            earlyBreakIfCodeBlock.append(")");

            codeBuilder.beginControlFlow(earlyBreakIfCodeBlock.toString());
            switch (returnParam.returnType()) {
                case ReturnType.BOXED_PRIMITIVE -> codeBuilder.addStatement("return null");
                case ReturnType.OPTIONAL -> codeBuilder.addStatement("return $T.empty()", Optional.class);
                case ReturnType.LIST -> codeBuilder.addStatement("return $T.emptyList()", Collections.class);
                case ReturnType.SET -> codeBuilder.addStatement("return $T.emptySet()", Collections.class);
                default -> codeBuilder.addStatement("throw new $T(\"All params are null\")", RuntimeException.class);
            }
            codeBuilder.endControlFlow();
        }

        if (BuilderConstant.REQUEST_TYPES_TO_BATCH.contains(requestType)
              && ReturnType.VOID.equals(returnParam.returnType())
              && params.size() == 1
              && ParamType.COLLECTION.equals(params.get(0).type())) {
            isBatch = true;
        }

        if (fieldFiller) {
            if (params.size() != 1) {
                throw new RuntimeException("Field filler method must have only one parameter - search ID [" + methodReference + "]");
            }

            if (!returnParam.returnType().isFieldFilling()) {
                throw new RuntimeException("Field filler method must return correct returnType () [" + methodReference + "]");
            }

            codeBuilder.beginControlFlow(
                  "if (" + cacheName + ".hasValue($T.class, $T.class, " + params.get(0).spec().name + "))",
                  returnParam.getGenericType(), returnParam.genericParam()
            ).addStatement(
                  "return ($T<$T>) " + cacheName + ".get($T.class, $T.class, " + params.get(0).spec().name + ")",
                  returnParam.getGenericType(), returnParam.genericParam(), returnParam.getGenericType(), returnParam.genericParam()
            ).endControlFlow();
        }

        final var preparedRequestPair = prepareRequest(request);

        if (isTransactional) {
            codeBuilder.addStatement("final var " + BuilderConstant.CONNECTION_NAME + " = " + connectionManagerName + ".getConnection()");
        } else {
            codeBuilder.beginControlFlow("try (final var " + BuilderConstant.CONNECTION_NAME + " = " + connectionManagerName + ".getConnection())");
        }
        codeBuilder.beginControlFlow("try (final var " + BuilderConstant.STATEMENT_NAME + " = " + BuilderConstant.CONNECTION_NAME + ".prepareStatement(\n\""
              + preparedRequestPair.first() + "\"\n))");

        final var indexToRequestParam = preparedRequestPair.second();

        if (isBatch) {
            codeBuilder.addStatement(BuilderConstant.CONNECTION_NAME + ".setAutoCommit(false)");
        }

        if (!indexToRequestParam.isEmpty()) {
            if (isBatch) {
                codeBuilder.beginControlFlow("for ($T " + BuilderConstant.RECORD_NAME + " : " + params.get(0).spec().name + ")", params.get(0).genericTypeMirror());
            }
            indexToRequestParam.forEach((index, requestParam) -> {
                      if (isBatch) {
                          addSetMethodToPrepareStatementBatch(codeBuilder, BuilderConstant.STATEMENT_NAME, index, requestParam);
                          return;
                      }
                      addSetMethodToPrepareStatement(codeBuilder, BuilderConstant.STATEMENT_NAME, index, requestParam);
                  }
            );
            if (isBatch) {
                codeBuilder.addStatement(BuilderConstant.STATEMENT_NAME + ".addBatch()");
                codeBuilder.endControlFlow();
            }
        }

        addResultBlockStatement(codeBuilder);

        if (!isTransactional) {
            codeBuilder.endControlFlow();
        }
        codeBuilder.nextControlFlow("catch ($T e)", SQLException.class);
        codeBuilder.addStatement("throw new $T(e)", RuntimeException.class);
        codeBuilder.endControlFlow();

        return codeBuilder.build();
    }

    private Pair<String, Map<Integer, RequestParam>> prepareRequest(String request) {
        if (params.isEmpty()) {
            return Pair.of(request, new HashMap<>());
        }

        final var paramNameToParamMap = getRequestParamByParamName();

        final var argTypes = new HashMap<Integer, RequestParam>(params.size() * 2);
        var index = 1;

        var preparedRequest = request;
        var indexOfCurrentParam = request.indexOf("#{");
        do {
            var requestStringParamsEnd = preparedRequest.substring(indexOfCurrentParam + 2);
            var paramName = requestStringParamsEnd.substring(0, requestStringParamsEnd.indexOf("}"));

            final var indexOfModification = paramName.indexOf(".");
            var modification = "";
            var modificationForRegex = "";

            if (indexOfModification > 0) {
                modification = paramName.substring(indexOfModification);
                modificationForRegex = modification.replace("(", "\\(").replace(")", "\\)");
                paramName = paramName.substring(0, indexOfModification);
            }

            final var requestParam = paramNameToParamMap.get(paramName);

            if (requestParam == null) {
                throw new RuntimeException("Can't find param " + paramName + " [" + methodReference + "]");
            }

            preparedRequest = preparedRequest.replaceFirst(
                  "#\\{" + requestParam.spec().name + modificationForRegex + "}",
                  "?"
            );

            argTypes.put(index++, new RequestParam(requestParam, modification));
            indexOfCurrentParam = preparedRequest.indexOf("#{");

            if (argTypes.size() > params.size() * MAX_MULTIPLIER_NUMBER_PARAMETERS) {
                throw new RuntimeException("Overflow is coming! [" + methodReference + "]");
            }
        } while (indexOfCurrentParam > 0);

        return Pair.of(preparedRequest, argTypes);
    }

    private Map<String, RequestParam> getRequestParamByParamName() {
        if (isBatch) {
            TypeMirror typeMirror = typeMirrorUtil.getGenericParamIfPossible(params.get(0).genericTypeMirror());
            return getFields(typeMirror)
                  .stream()
                  .collect(Collectors.toMap(
                        element -> element.getSimpleName().toString(),
                        element -> {
                            final var type = element.asType();
                            final var spec = ParameterSpec.builder(TypeName.get(type), element.getSimpleName().toString()).build();
                            final var genericType = typeMirrorUtil.getGenericParamIfPossible(type);
                            final var paramType = typeMirrorUtil.isCollection(type)
                                  ? ParamType.COLLECTION
                                  : ParamType.COMMON;
                            return new RequestParam(spec, genericType, paramType);
                        }
                  ));
        }

        return params.stream().collect(Collectors.toMap(p -> p.spec().name, Function.identity()));
    }

    private void addResultBlockStatement(CodeBlock.Builder codeBuilder) {
        if (ReturnType.VOID.equals(returnParam.returnType())) {
            if (isBatch) {
                codeBuilder.addStatement(BuilderConstant.STATEMENT_NAME + ".executeBatch()");
            } else {
                codeBuilder.addStatement(BuilderConstant.STATEMENT_NAME + ".executeUpdate()");
            }
            return;
        }

        codeBuilder.beginControlFlow("try(final var " + BuilderConstant.RESULT_SET_NAME + " = " + BuilderConstant.STATEMENT_NAME + ".executeQuery())");

        final var outputName = "output";
        if (returnParam.returnType().isCollection()) {
            addCollectionBlockStatement(codeBuilder, outputName);
        } else {
            addSingleValueBlockStatement(codeBuilder, outputName);
        }

        fillCache(codeBuilder, outputName);

        if (returnParam.returnType().isOptional()) {
            codeBuilder.addStatement("return $T.ofNullable(" + outputName + ")", Optional.class);
        } else {
            codeBuilder.addStatement("return " + outputName);
        }

        codeBuilder.endControlFlow();
    }

    private void addCollectionBlockStatement(CodeBlock.Builder codeBuilder, String outputName) {
        final var lambdaCodeBuilder = CodeBlock.builder();

        final var lambdaOutputName = "result";
        lambdaCodeBuilder.beginControlFlow("try");
        if (!typeMirrorUtil.canFillRow(returnParam.genericParam())) {
            lambdaCodeBuilder.addStatement("var " + lambdaOutputName + " = new $T()", returnParam.genericParam());
        }
        addClassTransformStatements(lambdaCodeBuilder, "row", lambdaOutputName, true);
        lambdaCodeBuilder.nextControlFlow("catch ($T e)", SQLException.class);
        lambdaCodeBuilder.addStatement("throw new $T(e)", RuntimeException.class);
        lambdaCodeBuilder.endControlFlow();

        final var convertFunction = TypeSpec.anonymousClassBuilder("")
              .addSuperinterface(
                    ParameterizedTypeName.get(
                          ClassName.get(Function.class), TypeName.get(ResultSet.class), TypeName.get(returnParam.genericParam())
                    )
              )
              .addMethod(MethodSpec.methodBuilder("apply")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ResultSet.class, "row")
                    .returns(TypeName.get(returnParam.genericParam()))
                    .addCode(lambdaCodeBuilder.build())
                    .build()
              )
              .build();

        var methodName = ".unknownMethod";
        if (ReturnType.SET.equals(returnParam.returnType())) {
            methodName = ".toSet";
            codeBuilder.addStatement("var " + outputName + " = new $T<$T>()", HashSet.class, returnParam.genericParam());
        } else if (ReturnType.LIST.equals(returnParam.returnType())) {
            methodName = ".toList";
            codeBuilder.addStatement("var " + outputName + " = new $T<$T>()", ArrayList.class, returnParam.genericParam());
        }

        fillCache(codeBuilder, outputName);

        codeBuilder.addStatement("output.addAll($T" + methodName + "(" + BuilderConstant.RESULT_SET_NAME + ", $L))", ResultSetUtil.class, convertFunction);
    }

    private void fillCache(CodeBlock.Builder codeBuilder, String valueName) {
        if (fieldFiller) {
            if (returnParam.returnType().isOptional()) {
                codeBuilder.addStatement(
                      cacheName + ".put($T.class, $T.class, " + params.get(0).spec().name + ", $T.ofNullable(" + valueName + "))",
                      returnParam.getGenericType(), returnParam.genericParam(), Optional.class
                );
            } else {
                codeBuilder.addStatement(
                      cacheName + ".put($T.class, $T.class, " + params.get(0).spec().name + ", " + valueName + ")",
                      returnParam.getGenericType(), returnParam.genericParam()
                );
            }
        }
    }

    private void addSingleValueBlockStatement(CodeBlock.Builder codeBuilder, String outputName) {
        if (typeMirrorUtil.canFillRow(returnParam.genericParam())) {
            codeBuilder.addStatement("$T " + outputName, returnParam.genericParam());
        } else {
            codeBuilder.addStatement("var " + outputName + " = new $T()", returnParam.genericParam());
        }

        fillCache(codeBuilder, outputName);

        codeBuilder.beginControlFlow("if(" + BuilderConstant.RESULT_SET_NAME + ".next())");
        addClassTransformStatements(codeBuilder, BuilderConstant.RESULT_SET_NAME, outputName, false);
        if (ReturnType.PRIMITIVE.equals(returnParam.returnType())) {
            codeBuilder.nextControlFlow("else");
            codeBuilder.addStatement("throw new $T(\"No such value!\")", RuntimeException.class);
        } else if (!returnParam.returnType().isPrimitiveType()) {
            codeBuilder.nextControlFlow("else");
            codeBuilder.addStatement(outputName + " = null");
        }
        codeBuilder.endControlFlow();
    }

    private void addClassTransformStatements(CodeBlock.Builder codeBuilder, String resultSetName, String resultName, boolean addReturn) {
        if (typeMirrorUtil.canFillRow(returnParam.genericParam())) {
            addGetMethodFromResultSetStatement(
                  codeBuilder,
                  addReturn ? "return " : resultName + " = ",
                  resultSetName,
                  "",
                  "1",
                  "",
                  returnParam.genericParam()
            );
            return;
        }

        final var fields = getFields(returnParam.genericParam());

        addFillCommonFieldsStatement(codeBuilder, fields, resultName, resultSetName);

        if (fillSubfields) {
            addFillSubfieldsStatement(codeBuilder, fields, resultName, resultSetName);
        }

        if (addReturn) {
            if (returnParam.returnType().isOptional()) {
                codeBuilder.addStatement("return $T.ofNullable(" + resultName + ")", Optional.class);
            } else {
                codeBuilder.addStatement("return " + resultName);
            }
        }
    }

    private void addSetMethodToPrepareStatement(
          CodeBlock.Builder codeBuilder,
          String statementPrefix,
          int index,
          RequestParam requestParam
    ) {
        if (!requestParam.modification().isBlank()) {
            codeBuilder.addStatement(statementPrefix + ".setObject(" + index + ", " +
                  "isNull(" + requestParam.spec().name + ") ? null : " + requestParam.spec().name + requestParam.modification() + ")");
            return;
        }

        if (ParamType.COLLECTION.equals(requestParam.type())) {
            final var typeMirror = requestParam.genericTypeMirror();
            final var typeElement = typeMirrorUtil.asElement(typeMirror);
            var type = typeElement.getSimpleName().toString().toLowerCase();
            if (typeElement.getKind() == ElementKind.ENUM) {
                type = BuilderConstant.TYPE_DB_VARCHAR;
            } else if (typeMirrorUtil.isTypeMirrorSameAsClass(typeMirror, OffsetDateTime.class)) {
                type = BuilderConstant.TYPE_DB_TIMESTAMP_TZ;
            } else if (typeMirrorUtil.isTypeMirrorSameAsClass(typeMirror, LocalTime.class)) {
                type = BuilderConstant.TYPE_DB_TIME;
            }

            codeBuilder.addStatement(
                  statementPrefix + ".setArray(" + index + ", " +
                        BuilderConstant.CONNECTION_NAME + ".createArrayOf(\"" + type + "\", " +
                        requestParam.spec().name + ".toArray()))",
                  String.class, Collectors.class
            );
            return;
        }

        var typeName = TypeName.get(requestParam.genericTypeMirror());

        if (TypeName.INT.equals(typeName) || TypeName.BYTE.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setInt(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (TypeName.SHORT.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setShort(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (TypeName.LONG.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setLong(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (TypeName.FLOAT.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setFloat(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (TypeName.DOUBLE.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setDouble(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (TypeName.BOOLEAN.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setBoolean(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (TypeName.get(String.class).equals(typeName) || TypeName.CHAR.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setString(" + index + ", " + requestParam.spec().name + ")");
            return;
        }
        if (typeMirrorUtil.asElement(requestParam.genericTypeMirror()).getKind() == ElementKind.ENUM) {
            codeBuilder.addStatement(statementPrefix + ".setString(" + index + ", " + requestParam.spec().name + ".toString())");
            return;
        }
        codeBuilder.addStatement(statementPrefix + ".setObject(" + index + ", " + requestParam.spec().name + ")");
    }

    private void addSetMethodToPrepareStatementBatch(
          CodeBlock.Builder codeBuilder,
          String statementPrefix,
          int index,
          RequestParam requestParam
    ) {
        if (!requestParam.modification().isBlank()) {
            codeBuilder.addStatement(statementPrefix + ".setObject(" + index + ", " +
                  "isNull(" + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "()) " +
                  "? null : " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "()" + requestParam.modification() + ")");
            return;
        }

        if (ParamType.COLLECTION.equals(requestParam.type())) {
            final var typeMirror = requestParam.genericTypeMirror();
            final var typeElement = typeMirrorUtil.asElement(typeMirror);
            var type = typeElement.getSimpleName().toString().toLowerCase();
            if (typeElement.getKind() == ElementKind.ENUM) {
                type = BuilderConstant.TYPE_DB_VARCHAR;
            } else if (typeMirrorUtil.isTypeMirrorSameAsClass(typeMirror, OffsetDateTime.class)) {
                type = BuilderConstant.TYPE_DB_TIMESTAMP_TZ;
            } else if (typeMirrorUtil.isTypeMirrorSameAsClass(typeMirror, LocalTime.class)) {
                type = BuilderConstant.TYPE_DB_TIME;
            }

            codeBuilder.addStatement(
                  statementPrefix + ".setArray(" + index + ", " +
                        BuilderConstant.CONNECTION_NAME + ".createArrayOf(\"" + type + "\", " +
                        requestParam.spec().name + ".toArray()))",
                  String.class, Collectors.class
            );
            return;
        }

        var typeName = TypeName.get(requestParam.genericTypeMirror());

        if (TypeName.INT.equals(typeName) || TypeName.BYTE.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setInt(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (TypeName.SHORT.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setShort(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (TypeName.LONG.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setLong(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (TypeName.FLOAT.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setFloat(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (TypeName.DOUBLE.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setDouble(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (TypeName.BOOLEAN.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setBoolean(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (TypeName.get(String.class).equals(typeName) || TypeName.CHAR.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + ".setString(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
            return;
        }
        if (typeMirrorUtil.asElement(requestParam.genericTypeMirror()).getKind() == ElementKind.ENUM) {
            codeBuilder.addStatement(statementPrefix + ".setString(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name
                  + "().toString())");
            return;
        }
        codeBuilder.addStatement(statementPrefix + ".setObject(" + index + ", " + BuilderConstant.RECORD_NAME + "." + requestParam.spec().name + "())");
    }

    private void addFillCommonFieldsStatement(
          CodeBlock.Builder codeBuilder, Set<? extends Element> fields, String resultName, String resultSetName
    ) {
        fields.stream()
              .filter(element -> typeMirrorUtil.canFillRow(element.asType()))
              .forEach(element -> {
                  final var fieldName = element.getSimpleName().toString();
                  final var methodName = ".set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                  addGetMethodFromResultSetStatement(
                        codeBuilder,
                        resultName + methodName + "(",
                        resultSetName,
                        ")",
                        "\"" + dbKindFieldName(fieldName) + "\"",
                        fieldName,
                        element.asType()
                  );
              });
    }

    private void addFillSubfieldsStatement(
          CodeBlock.Builder codeBuilder, Set<? extends Element> fields, String resultName, String resultSetName
    ) {
        fields.stream()
              .filter(element -> !typeMirrorUtil.canFillRow(element.asType()))
              .forEach(element -> {
                  final var fieldName = element.getSimpleName().toString();
                  final var methodName = ".set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                  final var result = results.get(fieldName);

                  if (result == null || !result.converter().isBlank()) {
                      return;
                  }

                  final var isOne = !result.one().method().isBlank();
                  final var method = isOne ? result.one().method() : result.many().method();

                  final var prefix = resultName + methodName + "(" +
                        result.field() + BuilderConstant.DAO_NAME_SUFFIX + "." + method + "(";

                  var postfix = ")";
                  if (isOne) {
                      if (result.one().required()) {
                          postfix += ".orElseThrow())";
                      } else {
                          postfix += ".orElse(null))";
                      }
                  } else {
                      postfix += ")";
                  }

                  addGetMethodFromResultSetStatement(
                        codeBuilder,
                        prefix,
                        resultSetName,
                        postfix,
                        "\"" + result.column() + "\"",
                        fieldName,
                        typeMirrorUtil.asTypeMirror(result.columnType())
                  );
              });
    }

    private void addGetMethodFromResultSetStatement(
          CodeBlock.Builder codeBuilder,
          String statementPrefix,
          String resultSetName,
          String statementPostfix,
          String elementName,
          String fieldName,
          TypeMirror type,
          Class<?>... prefixTypes
    ) {
        final var fieldNameResult = results.get(fieldName);
        if (fieldNameResult != null && !fieldNameResult.converter().isBlank()) {
            final var converter = ClassName.bestGuess(fieldNameResult.converter());
            final var types = buildTypeArray(null, converter, prefixTypes);
            codeBuilder.addStatement(
                  statementPrefix + "$T.convert(" + resultSetName + ".getString(" + elementName + "))" + statementPostfix, types
            );
            return;
        }

        var typeName = TypeName.get(type);

        if (TypeName.INT.equals(typeName) || TypeName.BYTE.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getInt(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }
        if (TypeName.SHORT.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getShort(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }
        if (TypeName.LONG.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getLong(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }
        if (TypeName.FLOAT.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getFloat(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }
        if (TypeName.DOUBLE.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getDouble(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }
        if (TypeName.BOOLEAN.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getBoolean(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }
        if (TypeName.get(String.class).equals(typeName) || TypeName.CHAR.equals(typeName)) {
            codeBuilder.addStatement(statementPrefix + resultSetName + ".getString(" + elementName + ")" + statementPostfix, prefixTypes);
            return;
        }

        final var types = buildTypeArray(type, null, prefixTypes);
        if (typeMirrorUtil.asElement(type).getKind() == ElementKind.ENUM) {
            codeBuilder.addStatement(statementPrefix + "$T.valueOf(" + resultSetName + ".getString(" + elementName + "))" + statementPostfix, types);
            return;
        }

        codeBuilder.addStatement(statementPrefix + resultSetName + ".getObject(" + elementName + ", $T.class)" + statementPostfix, types);
    }

    private Object[] buildTypeArray(TypeMirror type, ClassName classType, Class<?>... prefixTypes) {
        final var types = new ArrayList<>(Arrays.stream(prefixTypes)
              .filter(Objects::nonNull)
              .map(TypeName::get)
              .toList());

        if (Objects.nonNull(type)) {
            types.add(TypeName.get(type));
        }

        if (Objects.nonNull(classType)) {
            types.add(classType);
        }

        return types.toArray();
    }

    private String dbKindFieldName(String fieldName) {
        if (isNull(fieldName) || fieldName.isBlank()) {
            return "undefined";
        }

        if (fieldName.matches(".*\\d+.+")) {
            return fieldName.replaceAll("(\\w)(\\d+)(\\w+)", "$1_$2_$3").trim().toLowerCase();
        }

        return fieldName.replaceAll("([a-z])([A-Z]+)", "$1_$2").trim().toLowerCase();
    }

    private Set<? extends Element> getFields(TypeMirror typeMirror) {
        return typeMirrorUtil.getTypeElement(typeMirror)
              .getEnclosedElements()
              .stream()
              .filter(element -> ElementKind.FIELD.equals(element.getKind()))
              .collect(Collectors.toSet());
    }

}
