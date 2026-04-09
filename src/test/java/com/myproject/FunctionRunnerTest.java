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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.myproject.mocks.MockHttpRequest;
import com.myproject.mocks.MockHttpResponse;

import ortus.boxlang.runtime.gcp.FunctionRunner;

/**
 * Integration tests for the project's BoxLang handlers in {@code src/main/bx/}.
 * <p>
 * Each test exercises the full execution path: HTTP request → BoxLang execution
 * → HTTP response. The BoxLang runtime is started once (cold start) and reused
 * across all tests via a shared {@link FunctionRunner} instance.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FunctionRunnerTest {

	/** Points directly at the project's real Lambda.bx handler */
	private static final Path LAMBDA_PATH = Path.of( "src", "main", "bx", "Lambda.bx" );

	/** Shared runner — runtime init happens once for the whole test class */
	private FunctionRunner runner;

	@BeforeAll
	void setUpRunner() {
		runner = new FunctionRunner( LAMBDA_PATH, true );
	}

	// =========================================================================
	// Lifecycle & basic correctness
	// =========================================================================

	@Test
	@DisplayName( "Throws RuntimeException when Lambda.bx does not exist" )
	public void testLambdaNotFound() {
		FunctionRunner		missingRunner	= new FunctionRunner( Path.of( "invalid", "Lambda.bx" ), true );
		MockHttpRequest		req				= new MockHttpRequest( "GET", "/" );
		MockHttpResponse	res				= new MockHttpResponse();

		assertThrows( RuntimeException.class, () -> missingRunner.service( req, res ) );
	}

	@Test
	@DisplayName( "run() returns 200 and a JSON body with error/messages/data keys" )
	public void testRunReturns200WithJsonBody() throws Exception {
		MockHttpRequest		req	= new MockHttpRequest( "POST", "/" )
		    .withContentType( "application/json" )
		    .withBody( "{\"name\":\"Ortus Solutions\"}" );
		MockHttpResponse	res	= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
		assertThat( res.getBody() ).contains( "error" );
		assertThat( res.getBody() ).contains( "messages" );
		assertThat( res.getBody() ).contains( "data" );
	}

	@Test
	@DisplayName( "run() echoes incoming event data in the response body" )
	public void testRunEchoesEventData() throws Exception {
		MockHttpRequest		req	= new MockHttpRequest( "POST", "/" )
		    .withContentType( "application/json" )
		    .withBody( "{\"name\":\"Ortus Solutions\"}" );
		MockHttpResponse	res	= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
		assertThat( res.getBody() ).contains( "Ortus Solutions" );
	}

	// =========================================================================
	// x-bx-function header routing to alternative methods
	// =========================================================================

	@Test
	@DisplayName( "x-bx-function header routes to anotherLambda() in Lambda.bx" )
	public void testXBxFunctionHeaderRoutingToAnotherLambda() throws Exception {
		MockHttpRequest		req	= new MockHttpRequest( "GET", "/" )
		    .withHeader( "x-bx-function", "anotherLambda" );
		MockHttpResponse	res	= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
		assertThat( res.getBody() ).contains( "Hola" );
	}

	// =========================================================================
	// Response headers
	// =========================================================================

	@Test
	@DisplayName( "Response includes a Content-Type header" )
	public void testResponseHasContentTypeHeader() throws Exception {
		MockHttpRequest		req	= new MockHttpRequest( "GET", "/" );
		MockHttpResponse	res	= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getHeader( "Content-Type" ) ).isNotNull();
	}

	// =========================================================================
	// Edge cases
	// =========================================================================

	@Test
	@DisplayName( "Handles request with no headers gracefully" )
	public void testEmptyHeaders() throws Exception {
		MockHttpRequest		req	= new MockHttpRequest( "GET", "/" );
		MockHttpResponse	res	= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "Handles large request body without error" )
	public void testLargeRequestBody() throws Exception {
		String				largeBody	= "x".repeat( 100_000 );
		MockHttpRequest		req			= new MockHttpRequest( "POST", "/" )
		    .withContentType( "application/json" )
		    .withBody( "{\"largeData\":\"" + largeBody + "\"}" );
		MockHttpResponse	res			= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "Handles query parameters without error" )
	public void testQueryParameters() throws Exception {
		MockHttpRequest		req	= new MockHttpRequest( "GET", "/" )
		    .withQueryParam( "action", "greet" )
		    .withQueryParam( "name", "BoxLang" );
		MockHttpResponse	res	= new MockHttpResponse();

		runner.service( req, res );

		assertThat( res.getStatusCode() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "Handles concurrent invocations without data corruption" )
	public void testConcurrentInvocations() throws Exception {
		Thread[]	threads		= new Thread[ 5 ];
		int[]		statusCodes	= new int[ 5 ];

		for ( int i = 0; i < threads.length; i++ ) {
			final int idx = i;
			threads[ i ] = new Thread( () -> {
				try {
					MockHttpRequest		req	= new MockHttpRequest( "GET", "/" );
					MockHttpResponse	res	= new MockHttpResponse();
					runner.service( req, res );
					statusCodes[ idx ] = res.getStatusCode();
				} catch ( Exception e ) {
					statusCodes[ idx ] = 500;
				}
			} );
		}

		for ( Thread t : threads )
			t.start();
		for ( Thread t : threads )
			t.join( 10_000 );

		for ( int code : statusCodes )
			assertThat( code ).isEqualTo( 200 );
	}

	// =========================================================================
	// Accessor tests
	// =========================================================================

	@Test
	@DisplayName( "getDefaultFunctionPath returns the configured path" )
	public void testGetDefaultFunctionPath() {
		assertThat( runner.getDefaultFunctionPath().toString() ).contains( "Lambda.bx" );
	}

	@Test
	@DisplayName( "inDebugMode returns the configured value" )
	public void testInDebugMode() {
		assertThat( runner.inDebugMode() ).isTrue();
		assertThat( new FunctionRunner( LAMBDA_PATH, false ).inDebugMode() ).isFalse();
	}

	@Test
	@DisplayName( "getRuntime returns a non-null BoxRuntime" )
	public void testGetRuntime() {
		assertThat( runner.getRuntime() ).isNotNull();
	}
}
