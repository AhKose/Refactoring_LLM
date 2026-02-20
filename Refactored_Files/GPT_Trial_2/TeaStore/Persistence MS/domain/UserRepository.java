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
		PersistenceUser user = newUserFrom(entity);

		EntityManager em = getEM();
		try {
		em.getTransaction().begin();

		PersistenceUser existing = getUserByName(em, entity.getUserName());
		if (existing == null) {
			em.persist(user);
		} else {
			user.setId(-1L);
		}

		em.getTransaction().commit();
		return user.getId();
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean updateEntity(long id, User entity) {
		boolean found = false;
		EntityManager em = getEM();
	    try {
	        em.getTransaction().begin();
	        PersistenceUser user = em.find(getEntityClass(), id);
	        if (user != null) {
	        	user.setUserName(entity.getUserName());
	    		user.setPassword(entity.getPassword());
	    		user.setRealName(entity.getRealName());
	    		user.setEmail(entity.getEmail());
	        	found = true;
	        }
	        em.getTransaction().commit();
	    } finally {
	        em.close();
	    }
	    return found;
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
		EntityManager em = getEM();
		try {
		return getUserByName(em, userName);
		} finally {
		em.close();
		}
	}

	private static PersistenceUser newUserFrom(User entity) {
		PersistenceUser user = new PersistenceUser();
		user.setUserName(entity.getUserName());
		user.setPassword(entity.getPassword());
		user.setRealName(entity.getRealName());
		user.setEmail(entity.getEmail());
		return user;
	}

	private PersistenceUser getUserByName(EntityManager em, String userName) {
		TypedQuery<PersistenceUser> q =
			em.createQuery("SELECT u FROM " + getEntityClass().getName()
				+ " u WHERE u.userName = :name", getEntityClass())
			.setMaxResults(1);

		q.setParameter("name", userName);
		List<PersistenceUser> entities = q.getResultList();
		if (entities == null || entities.isEmpty()) {
		return null;
		}
		return entities.get(0);
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
	
}
