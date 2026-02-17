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

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import tools.descartes.teastore.persistence.repository.AbstractPersistenceRepository;
import tools.descartes.teastore.entities.User;

/**
 * Repository that performs transactional CRUD operations for users on database.
 * @author Joakim von Kistowski
 *
 */
public final class UserRepository extends AbstractPersistenceRepository<User, PersistenceUser> {

	/**
	 * Singleton for the ProductRepository.
	 */
	public static final UserRepository REPOSITORY = new UserRepository();
	
	//Private constructor.
	private UserRepository() {
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long createEntity(User entity) {
		PersistenceUser user = new PersistenceUser();
		user.setEmail(entity.getEmail());
		user.setUserName(entity.getUserName());
		user.setPassword(entity.getPassword());
		user.setRealName(entity.getRealName());

		inTx(em -> {
			PersistenceUser existing = getUserByName(em, entity.getUserName());
			if (existing == null) {
				em.persist(user);
			} else {
				user.setId(-1L);
			}
		});
		return user.getId();
	}

	@Override
	public boolean updateEntity(long id, User entity) {
		return inTx(em -> {
			PersistenceUser user = em.find(getEntityClass(), id);
			if (user == null) {
				return false;
			}
			user.setEmail(entity.getEmail());
			user.setPassword(entity.getPassword());
			user.setRealName(entity.getRealName());
			return true;
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long getId(PersistenceUser v) {
		return v.getId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<PersistenceUser> getEntityClass() {
		return PersistenceUser.class;
	}
	
	/**
	 * Return the user with the name.
	 * @param userName The user name.
	 * @return User or null if the user doesn't exist.
	 */
	public PersistenceUser getUserByName(String userName) {
		return withEm(em -> getUserByName(em, userName));
	}

	private PersistenceUser getUserByName(EntityManager em, String userName) {
		TypedQuery<PersistenceUser> allMatchesQuery =
				em.createQuery("SELECT u FROM " + getEntityClass().getName()
						+ " u WHERE u.userName = :name", getEntityClass())
				.setMaxResults(1);
		allMatchesQuery.setParameter("name", userName);
		List<PersistenceUser> entities = allMatchesQuery.getResultList();
		if (entities == null || entities.isEmpty()) {
			return null;
		}
		return entities.get(0);
	}

	private <R> R withEm(java.util.function.Function<EntityManager, R> work) {
		EntityManager em = getEM();
		try {
			return work.apply(em);
		} finally {
			em.close();
		}
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
	
}
