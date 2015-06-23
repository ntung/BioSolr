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
package uk.co.flax.biosolr;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to generate a facet tree.
 */
public class FacetTreeGenerator {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FacetTreeGenerator.class);
	
	private final FacetTreeBuilder treeBuilder;
	private final String collection;
	private final boolean pruneTrees;
	
	public FacetTreeGenerator(FacetTreeBuilder treeBuilder, String collection, boolean prune) {
		this.treeBuilder = treeBuilder;
		this.collection = collection;
		this.pruneTrees = prune;
	}
	
	public List<SimpleOrderedMap<Object>> generateTree(ResponseBuilder rb, NamedList<Integer> facetValues) throws IOException {
		List<SimpleOrderedMap<Object>> retVal = null;
		
		// First get the searcher for the required collection
		RefCounted<SolrIndexSearcher> searcherRef = getSearcherReference(rb);
		
		try {
			// Build the facet tree(s)
			Collection<TreeFacetField> fTrees = treeBuilder.processFacetTree(searcherRef.get(), extractFacetValues(facetValues));
			LOGGER.debug("Extracted {} facet trees", fTrees.size());
			
			if (pruneTrees) {
				// Prune the trees
				fTrees = prune(fTrees);
			}

			// Convert the trees into a SimpleOrderedMap
			retVal = convertTreeFacetFields(fTrees);
		} finally {
			// Make sure the search ref count is decreased
			searcherRef.decref();
		}
		
		return retVal;
	}
	
	/**
	 * Get a reference to the searcher for the required collection. If the collection is
	 * not the same as the search collection, we assume it is under the same Solr instance.
	 * @param rb the response builder holding the facets.
	 * @return a counted reference to the searcher.
	 * @throws SolrException if the collection cannot be found.
	 */
	private RefCounted<SolrIndexSearcher> getSearcherReference(ResponseBuilder rb) throws SolrException {
		RefCounted<SolrIndexSearcher> searcherRef;
		
		SolrCore currentCore = rb.req.getCore();
		if (StringUtils.isBlank(collection)) {
			searcherRef = currentCore.getSearcher();
		} else {
			// Using an alternative core - find it
			SolrCore reqCore = currentCore.getCoreDescriptor().getCoreContainer().getCore(collection);
			if (reqCore == null) {
				throw new SolrException(ErrorCode.BAD_REQUEST, "Collection \"" + collection
						+ "\" cannot be found");
			}
			searcherRef = reqCore.getSearcher();
		}
		
		return searcherRef;
	}
	
	/**
	 * Convert a list of facets into a map, keyed by the facet term. 
	 * @param facetValues the facet values.
	 * @return a map of term - value for each entry.
	 */
	private Map<String, Integer> extractFacetValues(NamedList<Integer> facetValues) {
		Map<String, Integer> facetMap = new LinkedHashMap<>();
		for (Iterator<Entry<String, Integer>> it = facetValues.iterator(); it.hasNext(); ) {
			Entry<String, Integer> entry = it.next();
			if (entry.getValue() > 0) {
				facetMap.put(entry.getKey(), entry.getValue());
			}
		}
		
		return facetMap;
	}
	
	private Collection<TreeFacetField> prune(Collection<TreeFacetField> unpruned) {
		// Prune the trees
		Collection<TreeFacetField> pruned = stripNonRelevantTrees(unpruned);
		
		// Now loop through the top-level nodes, making sure none of the entries
		// are included in another entry's children
		pruned = deduplicateTrees(pruned);
		
		return pruned;
	}
	
	private Collection<TreeFacetField> deduplicateTrees(Collection<TreeFacetField> trees) {
		return trees.stream().filter(t -> !isFacetInChildren(t, 0, trees)).collect(Collectors.toList());
	}
	
	private boolean isFacetInChildren(TreeFacetField facet, int level, Collection<TreeFacetField> trees) {
		boolean retVal = false;
		
		if (trees != null) {
			for (TreeFacetField tree : trees) {
				if ((level != 0 && tree.equals(facet)) || (isFacetInChildren(facet, level + 1, tree.getHierarchy()))) {
					retVal = true;
					break;
				}
			}
		}
		
		return retVal;
	}
	
	/**
	 * Prune a collection of facet trees, in order to remove nodes which are
	 * unlikely to be relevant. "Relevant" is defined here to be either
	 * entries with direct hits, or entries with a pre-defined number of
	 * child nodes with direct hits. This can remove several top-level
	 * layers from the tree which don't have direct hits.
	 * @param unprunedTrees the trees which need pruning.
	 * @return a sorted list of pruned trees.
	 */
	private Collection<TreeFacetField> stripNonRelevantTrees(Collection<TreeFacetField> unprunedTrees) {
		// Use a sorted set so the trees come out in count-descending order
		Set<TreeFacetField> pruned = new TreeSet<>(Comparator.reverseOrder());
		
		for (TreeFacetField tff : unprunedTrees) {
			if (tff.getCount() > 0) {
				// Relevant  - entry has direct hits
				pruned.add(tff);
			} else if (checkChildCounts(tff)) {
				// Relevant - entry has a number of children with direct hits
				pruned.add(tff);
			} else if (tff.hasChildren()) {
				// Not relevant at this level - recurse through children
				pruned.addAll(stripNonRelevantTrees(tff.getHierarchy()));
			}
		}
		
		return pruned;
	}
	
	/**
	 * Check whether the given tree has enough children with direct hits to 
	 * be included in the pruned tree.
	 * @param tree the facet tree.
	 * @return <code>true</code> if the tree has enough children to be 
	 * included.
	 */
	private boolean checkChildCounts(TreeFacetField tree) {
		int hitCount = 0;
		
		if (tree.hasChildren()) {
			for (TreeFacetField tff : tree.getHierarchy()) {
				if (tff.getCount() > 0) {
					hitCount ++;
				}
			}
		}
		
		return hitCount > 3;
	}
	
	
	/**
	 * Convert the tree facet fields into a list of SimpleOrderedMaps, so they can
	 * be easily serialized by Solr.
	 * @param fTrees the list of facet tree fields.
	 * @return a list of equivalent maps.
	 */
	private List<SimpleOrderedMap<Object>> convertTreeFacetFields(Collection<TreeFacetField> fTrees) {
		return fTrees.stream().map(TreeFacetField::toMap).collect(Collectors.toList());
	}

}
