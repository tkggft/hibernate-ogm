/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.ogm.test.mongodb.query.parsing;

import static org.fest.assertions.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.QueryParser;
import org.hibernate.hql.ast.spi.EntityNamesResolver;
import org.hibernate.ogm.dialect.mongodb.query.parsing.MongoDBProcessingChain;
import org.hibernate.ogm.dialect.mongodb.query.parsing.MongoDBQueryParsingResult;
import org.hibernate.ogm.test.mongodb.query.parsing.model.IndexedEntity;
import org.hibernate.ogm.test.utils.MapBasedEntityNamesResolver;
import org.hibernate.ogm.test.utils.OgmTestCase;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for {@link org.hibernate.ogm.dialect.mongodb.query.parsing.MongoDBQueryResolverDelegate} and
 * {@link org.hibernate.ogm.dialect.mongodb.query.parsing.MongoDBQueryRendererDelegate}.
 *
 * @author Gunnar Morling
 */
public class MongoDBQueryParsingTest extends OgmTestCase {

	private QueryParser queryParser;

	@Before
	public void setupParser() {
		queryParser = new QueryParser();
	}

	@Test
	public void shouldCreateUnrestrictedQuery() {
		assertMongoDbQuery(
				"from IndexedEntity",
				"{ }" );
	}

	@Test
	public void shouldCreateRestrictedQueryUsingSelect() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.title = 'same'",
				"{ \"title\" : \"same\"}" );
	}

	@Test
	public void shouldUseSpecialNameForIdPropertyInWhereClause() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.id = '1'",
				"{ \"_id\" : \"1\"}" );
	}

	@Test
	public void shouldUseColumnNameForPropertyInWhereClause() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.name = 'Bob'",
				"{ \"entityName\" : \"Bob\"}" );
	}

	@Test
	public void shouldCreateProjectionQuery() {
		MongoDBQueryParsingResult parsingResult = parseQuery( "select e.id, e.name, e.position from IndexedEntity e" );

		assertThat( parsingResult.getQuery().toString() ).isEqualTo( "{ }" );
		assertThat( parsingResult.getProjection().toString() ).isEqualTo( "{ \"_id\" : 1 , \"entityName\" : 1 , \"position\" : 1}" );
	}

	@Test
	public void shouldAddNumberPropertyAsNumber() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.position = 2",
				"{ \"position\" : 2}" );
	}

	@Test
	public void shouldCreateLessOrEqualQuery() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.position <= 20",
				"{ \"position\" : { \"$lte\" : 20}}" );
	}

	@Test
	public void shouldCreateQueryWithNegationInWhereClause() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.name <> 'Bob'",
				"{ \"entityName\" : { \"$ne\" : \"Bob\"}}" );
	}

	@Test
	public void shouldCreateQueryWithNestedNegationInWhereClause() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where NOT e.name <> 'Bob'",
				"{ \"entityName\" : \"Bob\"}" );
	}

	@Test
	public void shouldCreateQueryUsingSelectWithConjunctionInWhereClause() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.title = 'same' and e.position = 1",
				"{ \"$and\" : [ " +
					"{ \"title\" : \"same\"} , " +
					"{ \"position\" : 1}" +
				"]}" );
	}

	@Test
	public void shouldCreateQueryWithNegationAndConjunctionInWhereClause() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where NOT ( e.name = 'Bob' AND e.position = 1 )",
				"{ \"$or\" : [ " +
					"{ \"entityName\" : { \"$ne\" : \"Bob\"}} , " +
					"{ \"position\" : { \"$ne\" : 1}}" +
				"]}" );
	}

	@Test
	public void shouldCreateNegatedRangeQuery() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.name = 'Bob' and not e.position between 1 and 3",
				"{ \"$and\" : [ " +
					"{ \"entityName\" : \"Bob\"} , " +
					"{ \"$or\" : [ " +
						"{ \"position\" : { \"$lt\" : 1}} , " +
						"{ \"position\" : { \"$gt\" : 3}}" +
					"]}" +
				"]}");
	}

	@Test
	public void shouldCreateBooleanQueryUsingSelect() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.name = 'same' or ( e.id = 4 and e.name = 'booh')",
				"{ \"$or\" : [ " +
					"{ \"entityName\" : \"same\"} , " +
					"{ \"$and\" : [ " +
						"{ \"_id\" : \"4\"} , " +
						"{ \"entityName\" : \"booh\"}" +
					"]}" +
				"]}" );
	}

	@Test
	public void shouldCreateNumericBetweenQuery() {
		Map<String, Object> namedParameters = new HashMap<String, Object>();
		namedParameters.put( "lower", 10L );
		namedParameters.put( "upper", 20L );

		assertMongoDbQuery(
				"select e from IndexedEntity e where e.position between :lower and :upper",
				namedParameters,
				"{ \"$and\" : [ " +
					"{ \"position\" : { \"$gte\" : 10}} , " +
					"{ \"position\" : { \"$lte\" : 20}}" +
				"]}");
	}

	@Test
	public void shouldCreateInQuery() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.title IN ( 'foo', 'bar', 'same')",
				"{ \"title\" : " +
					"{ \"$in\" : [ \"foo\" , \"bar\" , \"same\"]}" +
				"}" );
	}

	@Test
	public void shouldCreateNotInQuery() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.title NOT IN ( 'foo', 'bar', 'same')",
				"{ \"title\" : " +
					"{ \"$nin\" : [ \"foo\" , \"bar\" , \"same\"]}" +
				"}" );
	}

	@Test
	public void shouldCreateLikeQuery() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.title like 'Ali_e%')",
				"{ \"title\" : " +
					"{ \"$regex\" : \"^\\\\QAli\\\\E.\\\\Qe\\\\E.*$\" , " +
					"\"$options\" : \"s\"" +
					"}" +
				"}");
	}

	@Test
	public void shouldCreateNotLikeQuery() {
		assertMongoDbQuery(
				"select e from IndexedEntity e where e.title not like 'Ali_e%')",
				"{ \"title\" : " +
					"{ \"$not\" : " +
						"{ \"$regex\" : \"^\\\\QAli\\\\E.\\\\Qe\\\\E.*$\" , " +
						"\"$options\" : \"s\"" +
						"}" +
					"}" +
				"}");
	}

	private void assertMongoDbQuery(String queryString, String expectedMongoDbQuery) {
		assertMongoDbQuery( queryString, null, expectedMongoDbQuery );
	}

	private void assertMongoDbQuery(String queryString, Map<String, Object> namedParameters, String expectedMongoDbQuery) {
		MongoDBQueryParsingResult parsingResult = parseQuery( queryString, namedParameters );
		assertThat( parsingResult ).isNotNull();
		assertThat( parsingResult.getEntityType() ).isSameAs( IndexedEntity.class );

		if ( expectedMongoDbQuery == null ) {
			assertThat( parsingResult.getQuery() ).isNull();
		}
		else {
			assertThat( parsingResult.getQuery() ).isNotNull();
			assertThat( parsingResult.getQuery().toString() ).isEqualTo( expectedMongoDbQuery );
		}
	}

	private MongoDBQueryParsingResult parseQuery(String queryString) {
		return parseQuery( queryString, null );
	}

	private MongoDBQueryParsingResult parseQuery(String queryString, Map<String, Object> namedParameters) {
		return queryParser.parseQuery(
				queryString,
				setUpMongoDbProcessingChain( namedParameters )
				);
	}

	private MongoDBProcessingChain setUpMongoDbProcessingChain(Map<String, Object> namedParameters) {
		Map<String, Class<?>> entityNames = new HashMap<String, Class<?>>();
		entityNames.put( "com.acme.IndexedEntity", IndexedEntity.class );
		entityNames.put( "IndexedEntity", IndexedEntity.class );
		EntityNamesResolver nameResolver = new MapBasedEntityNamesResolver( entityNames );

		return new MongoDBProcessingChain( (SessionFactoryImplementor) sessions, nameResolver, namedParameters );
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] { IndexedEntity.class };
	}
}
