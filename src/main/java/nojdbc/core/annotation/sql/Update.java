package nojdbc.core.annotation.sql;

import nojdbc.core.enumeration.ReturnType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для выполнения команд по обновлению записей в таблице.<br>
 *
 * Поддерживает пакетные вставки.<br>
 * С помощью ключевого слова RETURNING, возможно возвращение данных обновленной строки целиком
 * или определенной колонки.<br>
 * Нельзя использовать возврат значений вместе с пакетными вставками.<br>
 * Возвращаемый тип должен соответствовать значениям {@link ReturnType}.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Update {

    String value();

}
