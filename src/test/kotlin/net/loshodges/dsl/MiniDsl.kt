package net.loshodges.dsl

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.Test


// represents a simple data access object
interface Dao {
    fun put(entity: String): Boolean
    fun delete(entity: String): Boolean
    fun get(entity: String): String?
    fun query(query: String): List<String>
}

// an in-memory representation of the inMemoryDao
// for demonstration purposes only
fun inMemoryDao(): Dao {
    return object : Dao {
        val filterMatch = "[a-zA-Z90-9]+".toRegex()

        val store: MutableSet<String> = mutableSetOf()
        override fun put(entity: String): Boolean {
            println("putting $entity")
            return store.add(entity)
        }

        override fun get(entity: String) = if (store.contains(entity)) {
            entity
        } else {
            null
        }

        override fun delete(entity: String): Boolean {
            println("deleting $entity")
            return store.remove(entity)
        }

        override fun query(query: String): List<String> {
            return if (query == "*") {
                store.toList()
            } else {
                query.split(" ")
                        .filter { filterMatch.matches(it) }
                        .filter { store.contains(it) }
            }

        }
    }
}


/**
 * example test patterns for
 * testing an application interaction
 * with a persistent storage layer
 * that persists across test runs
 */
class MiniDsl {

    val dao: Dao = inMemoryDao()

    @Test
    fun `simple OR query matches 2 entities`() {

        val query = "abc || def"
        val entity1 = "abc"
        val entity2 = "def"

        dao.put(entity1)
        dao.put(entity2)

        val results = dao.query(query)

        assertThat(results)
                .containsAll(listOf(entity1, entity2))
                .hasSize(2)

    }

    @Test
    fun `asterisk matches all sources`() {

        val query = "*"
        val entity1 = "abc"
        val entity2 = "def"

        dao.put(entity1)
        dao.put(entity2)

        val results = dao.query(query)

        assertThat(results)
                .containsAll(listOf(entity1, entity2))
                .hasSize(2)
    }

    @Test
    fun `test template`() {
        // setup
        val entity1 = "abc"
        val entity2 = "def"
        val query = "*"

        dao.put(entity1)
        dao.put(entity2)

        // act
        val results = dao.query(query)

        // assert
        assertThat(results)
                .containsAll(listOf(entity1, entity2))
                .hasSize(2)

        // cleanup
        dao.delete(entity1)
        dao.delete(entity2)

    }

    @Test
    fun `test template with await`() {
        // setup
        val entity1 = "abc"
        val entity2 = "def"
        val query = "*"

        dao.put(entity1)
        dao.put(entity2)

        await().until(
                Runnable {
                    assertThat(dao.get(entity1)).isNotNull()
                    assertThat(dao.get(entity2)).isNotNull()
                }
        )

        // act
        val results = dao.query(query)

        // assert
        assertThat(results)
                .containsAll(listOf(entity1, entity2))
                .hasSize(2)

        // cleanup
        dao.delete(entity1)
        dao.delete(entity2)

        await().until(
                Runnable {
                    assertThat(dao.get(entity1)).isNull()
                    assertThat(dao.get(entity2)).isNull()
                }
        )
    }

    @Test
    fun `test template with put - delete`() {
        // setup
        val entity1 = "abc"
        val entity2 = "def"
        val entities = listOf(entity1, entity2)
        val query = "*"

        put(entities)

        // act
        val results = dao.query(query)

        // assert
        assertThat(results)
                .containsAll(listOf(entity1, entity2))
                .hasSize(2)

        // cleanup
        delete(entities)
    }

    @Test
    fun `test template with put and delete - receiving entities`() {
        // setup
        val query = "*"
        putAndDelete(listOf("abc", "def")) {
            entities ->
            // act
            val results = dao.query(query)
            // assert
            assertThat(results)
                    .containsAll(entities)
                    .hasSize(2)
        }
    }

    fun putAndDelete(entities: List<String>, block: (List<String>) -> Unit) {
        put(entities)
        block(entities)
        delete(entities)
    }

    fun put(entities: List<String>) {
        entities.forEach { dao.put(it) }
        await().until(
                Runnable {
                    assertThat(entities.mapNotNull(dao::get))
                            .isEqualTo(entities)
                }
        )
    }

    fun delete(entities: List<String>) {
        entities.forEach { dao.delete(it) }
        await().until(
                Runnable {
                    assertThat(entities.mapNotNull(dao::get))
                            .hasSize(0)
                }
        )
    }
}
