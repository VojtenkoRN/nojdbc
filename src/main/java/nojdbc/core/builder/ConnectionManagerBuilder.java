package nojdbc.core.builder;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.agroal.api.AgroalDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionScoped;
import nojdbc.constant.BuilderConstant;
import nojdbc.core.cache.RequestCache;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ConnectionManagerBuilder {

    private final TypeSpec.Builder connectionManagerBuilder;
    private final Set<TypeName> interfaces;

    private ConnectionManagerBuilder(String connectionManagerInterfaceName) {
        interfaces = new HashSet<>();

        final var cacheField = FieldSpec.builder(RequestCache.class, BuilderConstant.CACHE_NAME)
              .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
              .initializer("$T.getInstance()", RequestCache.class)
              .build();

        final var dataSourceField = FieldSpec.builder(AgroalDataSource.class, BuilderConstant.DATASOURCE_NAME)
              .addAnnotation(Inject.class)
              .build();

        final var connectionField = FieldSpec.builder(Connection.class, BuilderConstant.CONNECTION_NAME)
              .addModifiers(Modifier.PRIVATE)
              .initializer("null")
              .build();

        connectionManagerBuilder = TypeSpec.classBuilder(connectionManagerInterfaceName)
              .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
              .addAnnotation(TransactionScoped.class)
              .addField(cacheField)
              .addField(dataSourceField)
              .addField(connectionField);
    }

    public static ConnectionManagerBuilder init(String connectionManagerInterfaceName) {
        return new ConnectionManagerBuilder(connectionManagerInterfaceName);
    }

    /**
     * Добавление реализуемых интерфейсов.
     *
     * @param interfaces список интерфейсов.
     * @return ConnectionManagerBuilder.
     */
    public ConnectionManagerBuilder addInterfaces(TypeMirror... interfaces) {
        this.interfaces.addAll(Arrays.stream(interfaces).map(TypeName::get).toList());
        return this;
    }

    /**
     * Получение спецификации объекта.
     *
     * @return спецификация объекта.
     */
    public TypeSpec build() {
        return connectionManagerBuilder
              .addMethod(generateOnBeginTransactionMethod())
              .addMethod(generateOnBeforeEndTransactionMethod())
              .addMethod(generateGetConnectionMethod())
              .addSuperinterfaces(interfaces)
              .build();
    }

    private MethodSpec generateOnBeginTransactionMethod() {
        final var codeBlock = CodeBlock.builder()
              .beginControlFlow("if ($T.isNull(" + BuilderConstant.CONNECTION_NAME + "))", Objects.class)
              .beginControlFlow("try")
              .addStatement(BuilderConstant.CONNECTION_NAME + " = " + BuilderConstant.DATASOURCE_NAME + ".getConnection()")
              .nextControlFlow("catch($T e)", SQLException.class)
              .addStatement("throw new $T(e)", RuntimeException.class)
              .endControlFlow()
              .endControlFlow()
              .build();

        return MethodSpec.methodBuilder("onBeginTransaction")
              .returns(TypeName.VOID)
              .addAnnotation(PostConstruct.class)
              .addCode(codeBlock)
              .build();
    }

    private MethodSpec generateOnBeforeEndTransactionMethod() {
        final var codeBlock = CodeBlock.builder()
              .beginControlFlow("if (!$T.isNull(" + BuilderConstant.CONNECTION_NAME + "))", Objects.class)
              .beginControlFlow("try")
              .addStatement(BuilderConstant.CONNECTION_NAME + ".close()")
              .addStatement(BuilderConstant.CONNECTION_NAME + " = null")
              .addStatement(BuilderConstant.CACHE_NAME + ".clean()")
              .nextControlFlow("catch($T e)", SQLException.class)
              .addStatement("throw new $T(e)", RuntimeException.class)
              .endControlFlow()
              .endControlFlow()
              .build();

        return MethodSpec.methodBuilder("onBeforeEndTransaction")
              .returns(TypeName.VOID)
              .addAnnotation(PreDestroy.class)
              .addCode(codeBlock)
              .build();
    }

    private MethodSpec generateGetConnectionMethod() {
        final var codeBlock = CodeBlock.builder()
              .beginControlFlow("if ($T.isNull(" + BuilderConstant.CONNECTION_NAME + "))", Objects.class)
              .addStatement("throw new $T(\"No active connection. You must start transaction to open new connection\")", RuntimeException.class)
              .endControlFlow()
              .addStatement("return " + BuilderConstant.CONNECTION_NAME)
              .build();

        return MethodSpec.methodBuilder("getConnection")
              .addModifiers(Modifier.PUBLIC)
              .returns(Connection.class)
              .addCode(codeBlock)
              .build();
    }

}
