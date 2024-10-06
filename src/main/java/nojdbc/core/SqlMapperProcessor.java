package nojdbc.core;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import nojdbc.core.annotation.result.Result;
import nojdbc.core.annotation.result.Results;
import nojdbc.core.builder.DaoBuilder;
import nojdbc.core.util.TypeMirrorUtil;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SqlMapperProcessor {

    private static final String DEFAULT_PACKAGE_NAME = "default";

    /**
     * Обработка элемента dao интерфейса и создание его реализации.
     *
     * @param env                        настройки для обработки аннотаций.
     * @param sqlMapperElement           элемента dao интерфейса.
     * @param connectionManagerClassName имя менеджера подключений к БД.
     * @param isTransactional            признак принадлежности к транзакции.
     */
    public void process(
          ProcessingEnvironment env, TypeElement sqlMapperElement, ClassName connectionManagerClassName, boolean isTransactional
    ) {
        final var typeMirrorUtils = new TypeMirrorUtil(env.getTypeUtils(), env.getElementUtils());
        final var resultsAnnotation = sqlMapperElement.getAnnotation(Results.class);

        List<Result> resultAnnotationList = Collections.emptyList();
        if (resultsAnnotation == null) {
            final var resultAnnotation = sqlMapperElement.getAnnotation(Result.class);
            if (resultAnnotation != null) {
                resultAnnotationList = List.of(resultAnnotation);
            }
        } else {
            resultAnnotationList = List.of(resultsAnnotation.value());
        }

        final var daoBuilder = DaoBuilder.init(
                    typeMirrorUtils,
                    sqlMapperElement.getSimpleName().toString(),
                    connectionManagerClassName,
                    isTransactional,
                    resultAnnotationList
              )
              .addInterfaces(sqlMapperElement.asType());

        sqlMapperElement.getEnclosedElements()
              .stream()
              .filter(element -> ElementKind.METHOD.equals(element.getKind()))
              .map(element -> (ExecutableElement) element)
              .forEach(daoBuilder::addMethod);

        var packageName = typeMirrorUtils.getPackage(sqlMapperElement, DEFAULT_PACKAGE_NAME);

        var daoFile = JavaFile.builder(packageName, daoBuilder.build())
              .addStaticImport(Objects.class, "isNull")
              .build();

        try {
            daoFile.writeTo(env.getFiler());
        } catch (IOException ex) {
            throw new RuntimeException("Can't save new class to " + packageName);
        }
    }

}
