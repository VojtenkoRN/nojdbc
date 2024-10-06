package nojdbc.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResultSetUtilTest {

    private ResultSet resultSet;

    @BeforeEach
    public void setUp() throws SQLException {
        resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("name")).thenReturn("Foo", "Bar");
        when(resultSet.getInt("age")).thenReturn(4, 2);
    }

    @Test
    public void testToList() throws SQLException {
        List<Person> people = ResultSetUtil.toList(resultSet, rs -> {
            try {
                return new Person(rs.getString("name"), rs.getInt("age"));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        assertNotNull(people);
        assertEquals(2, people.size());
        assertEquals(new Person("Foo", 4), people.get(0));
        assertEquals(new Person("Bar", 2), people.get(1));
    }

    @Test
    public void testToSet() throws SQLException {
        Set<Person> people = ResultSetUtil.toSet(resultSet, rs -> {
            try {
                return new Person(rs.getString("name"), rs.getInt("age"));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        assertNotNull(people);
        assertEquals(2, people.size());
        assertTrue(people.contains(new Person("Foo", 4)));
        assertTrue(people.contains(new Person("Bar", 2)));
    }

    private record Person(String name, int age) {

    }
}