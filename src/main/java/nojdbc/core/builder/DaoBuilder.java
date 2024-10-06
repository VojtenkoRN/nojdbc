package nojdbc.core.builder;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.agroal.api.AgroalDataSource;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nojdbc.constant.BuilderConstant;
import nojdbc.core.annotation.result.Result;
import nojdbc.core.annotation.sql.Delete;
import nojdbc.core.annotation.sql.Insert;
import nojdbc.core.annotation.sql.Select;
import nojdbc.core.annotation.sql.Update;
import nojdbc.core.cache.RequestCache;
import nojdbc.core.enumeration.ParamType;
import nojdbc.core.enumeration.RequestType;
import nojdbc.core.pojo.RequestParam;
import nojdbc.core.pojo.ReturnParam;
import nojdbc.core.util.TypeMirrorUtil;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DaoBuilder {

    private final String daoInterfaceName;
    private final TypeSpec.Builder daoClassBuilder;
    private final Map<String, Result> results;
    private final TypeMirrorUtil typeMirrorUtil;
    private final Set<TypeName> interfaces;
    private final boolean isTransactional;

    private DaoBuilder(
          TypeMirrorUtil typeMirrorUtil, String daoInterfaceName, ClassName cmClassName, boolean isTransactional, List<Result> results
    ) {
        this.isTransactional = isTransactional;
        this.daoInterfaceName = daoInterfaceName;
        interfaces = new HashSet<>();

        this.results = results.stream().filter(Objects::nonNull).collect(Collectors.toMap(Result::field, Function.identity()));
        this.typeMirrorUtil = typeMirrorUtil;

        final var cacheField = FieldSpec.builder(RequestCache.class, BuilderConstant.CACHE_NAME)
              .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
              .initializer("$T.getInstance()", RequestCache.class)
              .build();

        var connectionField = FieldSpec.builder(cmClassName, BuilderConstant.CONNECTION_MANAGER_NAME)
              .addAnnotation(Inject.class)
              .build();

        if (!isTransactional) {
            connectionField = FieldSpec.builder(AgroalDataSource.class, BuilderConstant.DATASOURCE_NAME)
                  .addAnnotation(Inject.class)
                  .build();
        }

        final var daoFields = getDaoFields(results);

        daoClassBuilder = TypeSpec.classBuilder(daoInterfaceName + BuilderConstant.IMPL_NAME_SUFFIX)
              .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
              .addAnnotation(ApplicationScoped.class)
              .addField(cacheField)
              .addField(connectionField)
              .addFields(daoFields);
    }

    public static DaoBuilder init(
          TypeMirrorUtil typeMirrorUtil, String daoInterfaceName, ClassName cmClassName, boolean isTransactional, List<Result> results
    ) {
        return new DaoBuilder(typeMirrorUtil, daoInterfaceName, cmClassName, isTransactional, results);
    }

    /**
     * Добавление интерфейсов в построитель.
     *
     * @param interfaces интерфейсы.
     * @return построитель.
     */
    public DaoBuilder addInterfaces(TypeMirror... interfaces) {
        this.interfaces.addAll(Arrays.stream(interfaces).map(TypeName::get).toList());
        return this;
    }

    /**
     * Добавление метода в построитель.
     *
     * @param method метод.
     * @return построитель.
     */
    public DaoBuilder addMethod(ExecutableElement method) {
        final var params = method.getParameters().stream()
              .map(variableElement -> {
                  final var type = variableElement.asType();
                  final var spec = ParameterSpec.get(variableElement);
                  final var genericType = typeMirrorUtil.getGenericParamIfPossible(type);
                  final var isCollection = typeMirrorUtil.isCollection(type);
                  return new RequestParam(spec, genericType, isCollection ? ParamType.COLLECTION : ParamType.COMMON);
              })
              .collect(Collectors.toList());

        final var insertAnnotation = method.getAnnotation(Insert.class);
        if (Objects.nonNull(insertAnnotation)) {
            addModifyMethod(insertAnnotation.value(), method.getSimpleName().toString(), params, RequestType.INSERT, method.getReturnType());
        }

        final var updateAnnotation = method.getAnnotation(Update.class);
        if (Objects.nonNull(updateAnnotation)) {
            addModifyMethod(updateAnnotation.value(), method.getSimpleName().toString(), params, RequestType.UPDATE, method.getReturnType());
        }

        final var deleteAnnotation = method.getAnnotation(Delete.class);
        if (Objects.nonNull(deleteAnnotation)) {
            addModifyMethod(deleteAnnotation.value(), method.getSimpleName().toString(), params, RequestType.DELETE, method.getReturnType());
        }

        final var selectAnnotation = method.getAnnotation(Select.class);
        if (Objects.nonNull(selectAnnotation)) {
            addSelectMethod(
                  selectAnnotation.value(),
                  selectAnnotation.fillSubfields(),
                  selectAnnotation.fieldFiller(),
                  method.getSimpleName().toString(),
                  params,
                  method.getReturnType()
            );
        }

        return this;
    }

    private void addModifyMethod(String request, String methodName, List<RequestParam> params, RequestType requestType, TypeMirror returnTypeMirror) {
        final var returnType = typeMirrorUtil.getReturnType(returnTypeMirror);
        final var genericParamTypeMirror = typeMirrorUtil.getGenericParamIfPossible(returnTypeMirror);
        final var genericParamReturnType = typeMirrorUtil.getReturnType(genericParamTypeMirror);
        final var daoCodeBuilder = DaoCodeBuilder.init(typeMirrorUtil, daoInterfaceName + "." + methodName)
              .setRequest(request)
              .setParams(params)
              .setReturnParam(new ReturnParam(returnTypeMirror, returnType, genericParamTypeMirror, genericParamReturnType))
              .setResults(results)
              .setTransactional(isTransactional)
              .setRequestType(requestType);

        final var method = MethodSpec.methodBuilder(methodName)
              .addAnnotation(Blocking.class)
              .addAnnotation(Override.class)
              .addModifiers(Modifier.PUBLIC)
              .addParameters(params.stream().map(RequestParam::spec).toList())
              .returns(TypeName.get(returnTypeMirror));

        if (isTransactional) {
            daoCodeBuilder.setConnectionManagerName(BuilderConstant.CONNECTION_MANAGER_NAME);
            method.addAnnotation(Transactional.class);
        } else {
            daoCodeBuilder.setConnectionManagerName(BuilderConstant.DATASOURCE_NAME);
        }

        daoClassBuilder.addMethod(
              method.addCode(daoCodeBuilder.build()).build()
        );
    }

    private void addSelectMethod(
          String request,
          boolean fillSubfields,
          boolean fieldFiller,
          String methodName,
          List<RequestParam> params,
          TypeMirror returnTypeMirror) {
        final var returnType = typeMirrorUtil.getReturnType(returnTypeMirror);
        final var genericParamTypeMirror = typeMirrorUtil.getGenericParamIfPossible(returnTypeMirror);
        final var genericParamReturnType = typeMirrorUtil.getReturnType(genericParamTypeMirror);

        final var code = DaoCodeBuilder.init(typeMirrorUtil, daoInterfaceName + "." + methodName)
              .setRequest(request)
              .setParams(params)
              .setReturnParam(new ReturnParam(returnTypeMirror, returnType, genericParamTypeMirror, genericParamReturnType))
              .setResults(results)
              .setCacheName(BuilderConstant.CACHE_NAME)
              .setFillSubfields(fillSubfields)
              .setFieldFiller(fieldFiller)
              .setTransactional(isTransactional)
              .setRequestType(RequestType.SELECT);

        final var method = MethodSpec.methodBuilder(methodName)
              .addAnnotation(Blocking.class)
              .addAnnotation(Override.class)
              .addModifiers(Modifier.PUBLIC)
              .addParameters(params.stream().map(RequestParam::spec).toList())
              .returns(TypeName.get(returnTypeMirror));

        if (isTransactional) {
            code.setConnectionManagerName(BuilderConstant.CONNECTION_MANAGER_NAME);
            method.addAnnotation(Transactional.class);
        } else {
            code.setConnectionManagerName(BuilderConstant.DATASOURCE_NAME);
        }

        daoClassBuilder.addMethod(
              method.addCode(code.build()).build()
        );
    }

    public TypeSpec build() {
        return daoClassBuilder.addSuperinterfaces(interfaces).build();
    }

    private List<FieldSpec> getDaoFields(List<Result> results) {
        return results.stream()
              .filter(Objects::nonNull)
              .filter(result -> result.converter().isBlank())
              .map(result -> {
                  String clazz = null;
                  String method = null;

                  if (!result.many().method().isBlank()) {
                      clazz = result.many().clazz();
                      method = result.many().method();
                  }

                  if (!result.one().method().isBlank()) {
                      clazz = result.one().clazz();
                      method = result.one().method();
                  }

                  if (method == null) {
                      return null;
                  }

                  final var implClassName = ClassName.bestGuess(clazz + BuilderConstant.IMPL_NAME_SUFFIX);
                  final var implField = result.field() + BuilderConstant.DAO_NAME_SUFFIX;

                  return FieldSpec.builder(implClassName, implField)
                        .addAnnotation(Inject.class)
                        .build();
              })
              .filter(Objects::nonNull)
              .toList();
    }

}
