/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.persistence.domain;

import jakarta.persistence.EntityManager;

import tools.descartes.teastore.persistence.repository.AbstractPersistenceRepository;
import tools.descartes.teastore.entities.Category;

/**
 * Repository that performs transactional CRUD operations cor Categories on database.
 * @author Joakim von Kistowski
 *
 */
public final class CategoryRepository extends AbstractPersistenceRepository<Category, PersistenceCategory> {

	/**
	 * Singleton for the CategoryRepository.
	 */
	public static final CategoryRepository REPOSITORY = new CategoryRepository();
	
	//Private constructor.
	private CategoryRepository() {
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long createEntity(Category entity) {
		PersistenceCategory category = newCategoryFrom(entity);

		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		em.persist(category);
		em.getTransaction().commit();
		return category.getId();
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	@Override
	public boolean updateEntity(long id, Category entity) {
		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		PersistenceCategory category = em.find(getEntityClass(), id);
		if (category == null) {
			em.getTransaction().commit();
			return false;
		}
		applyUpdates(category, entity);
		em.getTransaction().commit();
		return true;
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	private static PersistenceCategory newCategoryFrom(Category entity) {
		PersistenceCategory category = new PersistenceCategory();
		applyUpdates(category, entity);
		return category;
	}

	private static void applyUpdates(PersistenceCategory category, Category entity) {
		category.setName(entity.getName());
		category.setDescription(entity.getDescription());
	}

	private static void rollbackQuietly(EntityManager em) {
		try {
		if (em.getTransaction().isActive()) {
			em.getTransaction().rollback();
		}
		} catch (Exception ignore) {
		// keep behavior: do not throw from cleanup
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long getId(PersistenceCategory v) {
		return v.getId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<PersistenceCategory> getEntityClass() {
		return PersistenceCategory.class;
	}
	
}
