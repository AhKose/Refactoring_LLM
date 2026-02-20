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
		PersistenceOrder order = new PersistenceOrder();
		copyOrderFields(entity, order);
		EntityManager em = getEM();
		try {
			em.getTransaction().begin();
			PersistenceUser user = em.find(PersistenceUser.class, entity.getUserId());
			if (user != null) {
				order.setUser(user);
				em.persist(order);
			} else {
				order.setId(-1L);
			}
			em.getTransaction().commit();
		} finally {
			em.close();
		}
		return order.getId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean updateEntity(long id, Order entity) {
		boolean found = false;
		EntityManager em = getEM();
		try {
			em.getTransaction().begin();
			PersistenceOrder order = em.find(getEntityClass(), id);
			if (order != null) {
				copyOrderFields(entity, order);
				found = true;
			}
			em.getTransaction().commit();
		} finally {
			em.close();
		}
		return found;
	}

	private void copyOrderFields(Order source, PersistenceOrder target) {
		target.setTime(source.getTime());
		target.setTotalPriceInCents(source.getTotalPriceInCents());
		target.setAddressName(source.getAddressName());
		target.setAddress1(source.getAddress1());
		target.setAddress2(source.getAddress2());
		target.setCreditCardCompany(source.getCreditCardCompany());
		target.setCreditCardNumber(source.getCreditCardNumber());
		target.setCreditCardExpiryDate(source.getCreditCardExpiryDate());
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
