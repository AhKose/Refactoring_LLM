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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import tools.descartes.teastore.persistence.repository.AbstractPersistenceRepository;
import tools.descartes.teastore.entities.Order;

/**
 * Repository that performs transactional CRUD operations for orders on database.
 * @author Joakim von Kistowski
 *
 */
public final class OrderRepository extends AbstractPersistenceRepository<Order, PersistenceOrder> {

	/**
	 * Singleton for the ProductRepository.
	 */
	public static final OrderRepository REPOSITORY = new OrderRepository();
	
	//Private constructor.
	private OrderRepository() {
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long createEntity(Order entity) {
		PersistenceOrder order = newOrderFrom(entity);

		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		PersistenceUser user = em.find(PersistenceUser.class, entity.getUserId());
		if (user == null) {
			order.setId(-1L);
		} else {
			order.setUser(user);
			em.persist(order);
		}
		em.getTransaction().commit();
		return order.getId();
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	@Override
	public boolean updateEntity(long id, Order entity) {
		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		PersistenceOrder order = em.find(getEntityClass(), id);
		if (order == null) {
			em.getTransaction().commit();
			return false;
		}
		applyUpdates(order, entity);
		em.getTransaction().commit();
		return true;
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	private static PersistenceOrder newOrderFrom(Order entity) {
		PersistenceOrder order = new PersistenceOrder();
		applyUpdates(order, entity);
		return order;
	}

	private static void applyUpdates(PersistenceOrder order, Order entity) {
		order.setTime(entity.getTime());
		order.setTotalPriceInCents(entity.getTotalPriceInCents());
		order.setAddressName(entity.getAddressName());
		order.setAddress1(entity.getAddress1());
		order.setAddress2(entity.getAddress2());
		order.setCreditCardCompany(entity.getCreditCardCompany());
		order.setCreditCardNumber(entity.getCreditCardNumber());
		order.setCreditCardExpiryDate(entity.getCreditCardExpiryDate());
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
	 * Gets all Orders for the user with the specified userId.
	 * @param userId The id of the ordering user.
	 * @param start The index of the first order to return. Negative value to start at the beginning.
	 * @param limit The maximum number of order to return. Negative value to return all.
	 * @return List of orders with the specified user.
	 */
	public List<PersistenceOrder> getAllEntitiesWithUser(long userId, int start, int limit) {
		List<PersistenceOrder> entities = null;
		EntityManager em = getEM();
	    try {
	        em.getTransaction().begin();
	        PersistenceUser user = em.find(PersistenceUser.class, userId);
	        if (user != null) {
	        	TypedQuery<PersistenceOrder> allMatchesQuery =
	        			em.createQuery("SELECT u FROM " + getEntityClass().getName()
	        					+ " u WHERE u.user = :user", getEntityClass());
	        	allMatchesQuery.setParameter("user", user);
	    		entities = resultsWithStartAndLimit(em, allMatchesQuery, start, limit);
	        }
	        em.getTransaction().commit();
	    } finally {
	        em.close();
	    }
		if (entities == null) {
			return new ArrayList<PersistenceOrder>();
		}
		return entities;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long getId(PersistenceOrder v) {
		return v.getId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<PersistenceOrder> getEntityClass() {
		return PersistenceOrder.class;
	}
	
}
