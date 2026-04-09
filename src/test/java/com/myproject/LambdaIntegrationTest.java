/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.myproject;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.myproject.mocks.MockHttpRequest;
import com.myproject.mocks.MockHttpResponse;

import ortus.boxlang.runtime.gcp.FunctionRunner;

@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class LambdaIntegrationTest {

	private FunctionRunner runner;

	@BeforeEach
	void setUp() throws IOException {
		Path functionPath = Path.of( "src", "main", "bx", "Lambda.bx" );
		runner = new FunctionRunner( functionPath, true );
	}

	@DisplayName( "Test default run() function" )
	@Test
	public void testBasicExecution() throws IOException {
		MockHttpRequest	req	= new MockHttpRequest( "POST", "/" )
		    .withContentType( "application/json" )
		    .withBody( "{\"name\":\"Ortus Solutions\"}" );
		MockHttpResponse res = new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
		assertThat( res.getBody() ).contains( "Ortus Solutions" );
	}

	@Test
	@DisplayName( "Test anotherLambda() via x-bx-function header" )
	public void testAnotherFunction() throws IOException {
		MockHttpRequest	req	= new MockHttpRequest( "GET", "/" )
		    .withHeader( "x-bx-function", "anotherLambda" );
		MockHttpResponse res = new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
		assertThat( res.getBody() ).contains( "Hola" );
	}

	@Test
	@DisplayName( "Test with empty request body" )
	public void testEmptyBody() throws IOException {
		MockHttpRequest	req	= new MockHttpRequest( "GET", "/" );
		MockHttpResponse res = new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "Test Content-Type header is set in response" )
	public void testDefaultContentTypeHeader() throws IOException {
		MockHttpRequest	req	= new MockHttpRequest( "GET", "/" );
		MockHttpResponse res = new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getHeader( "Content-Type" ) ).isNotNull();
	}

	@Test
	@DisplayName( "Test with query parameters" )
	public void testQueryParameters() throws IOException {
		MockHttpRequest	req	= new MockHttpRequest( "GET", "/" )
		    .withQueryParam( "action", "greet" )
		    .withQueryParam( "name", "BoxLang" );
		MockHttpResponse res = new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "Test performance with large request body" )
	public void testLargePayload() throws IOException {
		StringBuilder largeData = new StringBuilder();
		for ( int i = 0; i < 1000; i++ ) {
			largeData.append( "This is test data line " ).append( i ).append( ". " );
		}

		MockHttpRequest	req	= new MockHttpRequest( "POST", "/" )
		    .withContentType( "application/json" )
		    .withBody( "{\"largeData\":\"" + largeData.toString() + "\"}" );
		MockHttpResponse res = new MockHttpResponse();

		long startTime		= System.currentTimeMillis();
		runner.service( req, res );
		long executionTime	= System.currentTimeMillis() - startTime;

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
		assertThat( executionTime ).isLessThan( 5000L );
		System.out.println( "Large payload test completed in " + executionTime + "ms" );
	}
}
