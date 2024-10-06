package nojdbc.core;

import com.squareup.javapoet.JavaFile;
import nojdbc.core.annotation.ConnectionManager;
import nojdbc.core.builder.ConnectionManagerBuilder;
import nojdbc.core.util.TypeMirrorUtil;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.io.IOException;

public class ConnectionManagerProcessor {

    private static final String DEFAULT_PACKAGE_NAME = "default";
    private static final String CONNECTION_MANAGER_CLASS_NAME = "NoJdbcConnectionManager";

    /**
     * Создание реализации интерфейса менеджера транзакций.
     *
     * @param env                      настройки для обработки аннотаций.
     * @param connectionManagerElement интерфейс менеджера транзакций.
     * @return имя созданного класса с адресом расположения.
     */
    public String process(ProcessingEnvironment env, TypeElement connectionManagerElement) {
        final var typeMirrorUtils = new TypeMirrorUtil(env.getTypeUtils(), env.getElementUtils());

        final var connectionManagerClass = ConnectionManagerBuilder.init(CONNECTION_MANAGER_CLASS_NAME)
              .build();

        final var packagePath = connectionManagerElement.getAnnotation(ConnectionManager.class).value();
        final var packageName = packagePath.isBlank() ? typeMirrorUtils.getPackage(connectionManagerElement, DEFAULT_PACKAGE_NAME) : packagePath;

        final var connectionManagerFile = JavaFile.builder(packageName, connectionManagerClass)
              .build();

        try {
            connectionManagerFile.writeTo(env.getFiler());
        } catch (IOException ex) {
            throw new RuntimeException("Can't save new class to " + packageName);
        }

        return packageName + "." + CONNECTION_MANAGER_CLASS_NAME;
    }

}
