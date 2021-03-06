/**
 * Copyright (c) 2014 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.flax.biosolr.ontology.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for the Jena request class.
 * 
 * @author Matt Pearce
 */
public class JenaRequestTest {

	private static final String SEARCH_REQUEST_PATH = "src/test/resources/jenaRequest.json";

	@Test
	public void testDeserialize() {
		ObjectMapper mapper = new ObjectMapper();
		try {
			JenaRequest test = mapper.readValue(new File(SEARCH_REQUEST_PATH), JenaRequest.class);
			assertNotNull(test);
		} catch (JsonParseException e) {
			fail("JSON parse failed: " + e.getMessage());
		} catch (JsonMappingException e) {
			fail("JSON mapping failed: " + e.getMessage());
		} catch (IOException e) {
			fail("IO exception: " + e.getMessage());
		}
	}

}
