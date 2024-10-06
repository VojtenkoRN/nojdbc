package nojdbc.core.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ResultSetUtil {

    /**
     * Маппинг из результирующего набора в список объектов с выходным типом.
     *
     * @param resultSet результирующий набор запроса.
     * @param transform правило преобразования объекта из результирующего набора в объект выходного типа.
     * @param <T>       тип выходных объектов.
     * @return список объектов с выходным типом.
     * @throws SQLException ошибка при чтении результирующего набора.
     */
    public static <T> List<T> toList(ResultSet resultSet, Function<ResultSet, T> transform) throws SQLException {
        final var result = new ArrayList<T>();
        while (resultSet.next()) {
            result.add(transform.apply(resultSet));
        }
        return result;
    }

    /**
     * Маппинг из результирующего набора в набор объектов с выходным типом.
     *
     * @param resultSet результирующий набор запроса.
     * @param transform правило преобразования объекта из результирующего набора в объект выходного типа.
     * @param <T>       тип выходных объектов.
     * @return набор объектов с выходным типом.
     * @throws SQLException ошибка при чтении результирующего набора.
     */
    public static <T> Set<T> toSet(ResultSet resultSet, Function<ResultSet, T> transform) throws SQLException {
        final var result = new HashSet<T>();
        while (resultSet.next()) {
            result.add(transform.apply(resultSet));
        }
        return result;
    }

    private ResultSetUtil() {
    }

}
