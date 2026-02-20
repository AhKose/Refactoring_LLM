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
import tools.descartes.teastore.entities.OrderItem;

/**
 * Repository that performs transactional CRUD operations for order items on database.
 * @author Joakim von Kistowski
 *
 */
public final class OrderItemRepository extends AbstractPersistenceRepository<OrderItem, PersistenceOrderItem> {

	/**
	 * Singleton for the ProductRepository.
	 */
	public static final OrderItemRepository REPOSITORY = new OrderItemRepository();
	
	//Private constructor.
	private OrderItemRepository() {
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long createEntity(OrderItem entity) {
		PersistenceOrderItem item = newOrderItemFrom(entity);

		EntityManager em = getEM();
		try {
		em.getTransaction().begin();

		if (!attachRelations(em, item, entity)) {
			item.setId(-1L);
		} else {
			em.persist(item);
		}

		em.getTransaction().commit();
		return item.getId();
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	@Override
	public boolean updateEntity(long id, OrderItem entity) {
		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		PersistenceOrderItem item = em.find(getEntityClass(), id);
		if (item == null) {
			em.getTransaction().commit();
			return false;
		}
		applyUpdates(item, entity);
		em.getTransaction().commit();
		return true;
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}
	
	/**
	 * Gets all order items for the given productId.
	 * @param productId The id of the product ordered.
	 * @param start The index of the first orderItem to return. Negative value to start at the beginning.
	 * @param limit The maximum number of orderItem to return. Negative value to return all.
	 * @return List of order items with the specified product.
	 */
	public List<PersistenceOrderItem> getAllEntitiesWithProduct(long productId, int start, int limit) {
		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		PersistenceProduct prod = em.find(PersistenceProduct.class, productId);
		List<PersistenceOrderItem> entities = (prod == null)
			? null
			: queryByRelation(em, "u.product = :prod", "prod", prod, start, limit);
		em.getTransaction().commit();
		return (entities == null) ? new ArrayList<PersistenceOrderItem>() : entities;
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	public List<PersistenceOrderItem> getAllEntitiesWithOrder(long orderId, int start, int limit) {
		EntityManager em = getEM();
		try {
		em.getTransaction().begin();
		PersistenceOrder order = em.find(PersistenceOrder.class, orderId);
		List<PersistenceOrderItem> entities = (order == null)
			? null
			: queryByRelation(em, "u.order = :order", "order", order, start, limit);
		em.getTransaction().commit();
		return (entities == null) ? new ArrayList<PersistenceOrderItem>() : entities;
		} catch (RuntimeException ex) {
		rollbackQuietly(em);
		throw ex;
		} finally {
		em.close();
		}
	}

	private static PersistenceOrderItem newOrderItemFrom(OrderItem entity) {
		PersistenceOrderItem item = new PersistenceOrderItem();
		applyUpdates(item, entity);
		return item;
	}

	private static void applyUpdates(PersistenceOrderItem item, OrderItem entity) {
		item.setQuantity(entity.getQuantity());
		item.setUnitPriceInCents(entity.getUnitPriceInCents());
	}

	private static boolean attachRelations(EntityManager em, PersistenceOrderItem item, OrderItem entity) {
		PersistenceProduct prod = em.find(PersistenceProduct.class, entity.getProductId());
		PersistenceOrder order = em.find(PersistenceOrder.class, entity.getOrderId());
		if (prod == null || order == null) {
		return false;
		}
		item.setProduct(prod);
		item.setOrder(order);
		return true;
	}

	private List<PersistenceOrderItem> queryByRelation(EntityManager em, String whereClause,
		String paramName, Object paramValue, int start, int limit) {
		TypedQuery<PersistenceOrderItem> q = em.createQuery(
			"SELECT u FROM " + getEntityClass().getName() + " u WHERE " + whereClause, getEntityClass());
		q.setParameter(paramName, paramValue);
		return resultsWithStartAndLimit(em, q, start, limit);
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
	protected long getId(PersistenceOrderItem v) {
		return v.getId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<PersistenceOrderItem> getEntityClass() {
		return PersistenceOrderItem.class;
	}
	
}
