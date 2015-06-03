/**
 * Copyright (c) 2015 Lemur Consulting Ltd.
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

import java.util.List;

/**
 * @author mlp
 *
 */
public class HierarchicalFacetEntry extends FacetEntry {

	private final String uri;
	private final long total;
	private final List<FacetEntry> hierarchy;

	/**
	 * @param label
	 * @param count
	 * @param total
	 * @param hierarchy
	 */
	public HierarchicalFacetEntry(String uri, String label, long count, long total, List<FacetEntry> hierarchy) {
		super(label, count);
		this.uri = uri;
		this.total = total;
		this.hierarchy = hierarchy;
	}

	public String getUri() {
		return uri;
	}

	public long getTotal() {
		return total;
	}

	public List<FacetEntry> getHierarchy() {
		return hierarchy;
	}

}
