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
package com.myproject.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.myproject.mocks.MockHttpRequest;
import com.myproject.mocks.MockHttpResponse;

import ortus.boxlang.runtime.gcp.FunctionRunner;

/**
 * Local GCF Function Runner
 *
 * Run your BoxLang GCF function locally without the full GCF invoker.
 * Useful for quick smoke tests straight from the IDE.
 *
 * For a full HTTP server experience use:
 *   ./gradlew runFunction
 *
 * System properties (all optional):
 *   -DeventFile=workbench/sampleRequests/event-local.json  (request body JSON)
 *   -Dmethod=POST                                           (HTTP method, default POST)
 *   -Dpath=/                                               (URI path, default /)
 */
public class LocalLambdaRunner {

	public static void main( String[] args ) {
		try {
			String requestFile	= System.getProperty( "eventFile", "workbench/sampleRequests/event-local.json" );
			String method		= System.getProperty( "method", "POST" );
			String path			= System.getProperty( "path", "/" );

			System.out.println( "BoxLang GCF Local Runner" );
			System.out.println( "Loading request body from: " + requestFile );

			String body = loadBodyFromFile( requestFile );

			MockHttpRequest req = new MockHttpRequest( method, path )
			    .withContentType( "application/json" )
			    .withBody( body );
			MockHttpResponse res = new MockHttpResponse();

			Path			functionPath	= Paths.get( "src", "main", "bx", "Lambda.bx" );
			FunctionRunner	runner			= new FunctionRunner( functionPath, true );

			System.out.println( "Executing GCF function..." );
			long startTime = System.currentTimeMillis();

			runner.service( req, res );

			long executionTime = System.currentTimeMillis() - startTime;

			System.out.println( "Completed in " + executionTime + "ms" );
			System.out.println( "Status Code : " + res.getStatusCode() );
			System.out.println( "Content-Type: " + res.getHeader( "Content-Type" ) );
			System.out.println( "Body:" );
			System.out.println( res.getBody() );

			System.exit( 0 );
		} catch ( Exception e ) {
			System.err.println( "Error running GCF function locally:" );
			e.printStackTrace();
			System.exit( 1 );
		}
	}

	private static String loadBodyFromFile( String requestFile ) throws IOException {
		Path filePath = Paths.get( requestFile );
		if ( !Files.exists( filePath ) ) {
			System.out.println( "Request file not found: " + requestFile + " — using empty body" );
			return "{}";
		}
		return Files.readString( filePath );
	}
}
