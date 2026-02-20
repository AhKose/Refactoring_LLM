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
		PersistenceCategory category = new PersistenceCategory();
		category.setName(entity.getName());
		category.setDescription(entity.getDescription());

		inTx(em -> em.persist(category));
		return category.getId();
	}

	@Override
	public boolean updateEntity(long id, Category entity) {
		return inTx(em -> {
			PersistenceCategory category = em.find(getEntityClass(), id);
			if (category == null) {
				return false;
			}
			category.setName(entity.getName());
			category.setDescription(entity.getDescription());
			return true;
		});
	}

	private <R> R inTx(java.util.function.Function<EntityManager, R> work) {
		EntityManager em = getEM();
		try {
			em.getTransaction().begin();
			try {
				R result = work.apply(em);
				em.getTransaction().commit();
				return result;
			} catch (RuntimeException e) {
				rollbackQuietly(em);
				throw e;
			}
		} finally {
			em.close();
		}
	}

	private void inTx(java.util.function.Consumer<EntityManager> work) {
		inTx(em -> {
			work.accept(em);
			return null;
		});
	}

	private static void rollbackQuietly(EntityManager em) {
		try {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
		} catch (Exception ignored) {
			// best-effort rollback
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
