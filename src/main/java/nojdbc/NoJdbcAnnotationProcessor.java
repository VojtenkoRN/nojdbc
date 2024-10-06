package nojdbc;

import com.squareup.javapoet.ClassName;
import nojdbc.core.ConnectionManagerProcessor;
import nojdbc.core.SqlMapperProcessor;
import nojdbc.core.annotation.ConnectionManager;
import nojdbc.core.annotation.SqlMapper;
import nojdbc.core.annotation.result.Results;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Comparator;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("nojdbc.core.annotation.*")
public class NoJdbcAnnotationProcessor extends AbstractProcessor {

    private static final Object MONITOR = new Object();

    private SqlMapperProcessor sqlMapperProcessor;
    private ConnectionManagerProcessor connectionManagerProcessor;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        synchronized (MONITOR) {
            super.init(processingEnv);
            sqlMapperProcessor = new SqlMapperProcessor();
            connectionManagerProcessor = new ConnectionManagerProcessor();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            System.out.println("Code generation skipped. No data");
            return true;
        }

        final var connectionManagerClassFullPath = connectionManagerProcessor.process(
              processingEnv,
              (TypeElement) roundEnv.getElementsAnnotatedWith(ConnectionManager.class)
                    .stream()
                    .filter(element -> ElementKind.INTERFACE.equals(element.getKind()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("You mast have @ConnectionManager interface"))
        );

        final var connectionManagerClassName = ClassName.bestGuess(connectionManagerClassFullPath);
        roundEnv.getElementsAnnotatedWith(SqlMapper.class)
              .stream()
              .filter(element -> ElementKind.INTERFACE.equals(element.getKind()))
              .sorted(Comparator.comparing(element -> {
                  final var results = element.getAnnotation(Results.class);
                  if (results == null || results.value() == null) {
                      return 0;
                  }
                  return results.value().length;
              }))
              .forEach(element -> {
                  final var isTransactional = element.getAnnotation(SqlMapper.class).transactional();
                  sqlMapperProcessor.process(processingEnv, (TypeElement) element, connectionManagerClassName, isTransactional);
              });

        return true;
    }

}
