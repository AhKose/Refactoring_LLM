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
		PersistenceOrderItem orderItem = new PersistenceOrderItem();
		orderItem.setQuantity(entity.getQuantity());
		orderItem.setUnitPriceInCents(entity.getUnitPriceInCents());

		inTx(em -> {
			PersistenceProduct product = em.find(PersistenceProduct.class, entity.getProductId());
			PersistenceOrder order = em.find(PersistenceOrder.class, entity.getOrderId());
			if (product != null && order != null) {
				orderItem.setProduct(product);
				orderItem.setOrder(order);
				em.persist(orderItem);
			} else {
				orderItem.setId(-1L);
			}
		});
		return orderItem.getId();
	}

	@Override
	public boolean updateEntity(long id, OrderItem entity) {
		return inTx(em -> {
			PersistenceOrderItem orderItem = em.find(getEntityClass(), id);
			if (orderItem == null) {
				return false;
			}
			orderItem.setQuantity(entity.getQuantity());
			orderItem.setUnitPriceInCents(entity.getUnitPriceInCents());
			PersistenceProduct product = em.find(PersistenceProduct.class, entity.getProductId());
			PersistenceOrder order = em.find(PersistenceOrder.class, entity.getOrderId());
			if (product != null) {
				orderItem.setProduct(product);
			}
			if (order != null) {
				orderItem.setOrder(order);
			}
			return true;
		});
	}

	public List<PersistenceOrderItem> getAllEntitiesWithProduct(long productId) {
		List<PersistenceOrderItem> entities = inTx(em -> {
			PersistenceProduct product = em.find(PersistenceProduct.class, productId);
			if (product == null) {
				return null;
			}
			TypedQuery<PersistenceOrderItem> allMatchesQuery = em.createQuery("SELECT u FROM " + getEntityClass().getName()
					+ " u WHERE u.product = :product", getEntityClass());
			allMatchesQuery.setParameter("product", product);
			return allMatchesQuery.getResultList();
		});
		if (entities == null) {
			return new ArrayList<PersistenceOrderItem>();
		}
		return entities;
	}

	public List<PersistenceOrderItem> getAllEntitiesWithOrder(long orderId) {
		List<PersistenceOrderItem> entities = inTx(em -> {
			PersistenceOrder order = em.find(PersistenceOrder.class, orderId);
			if (order == null) {
				return null;
			}
			TypedQuery<PersistenceOrderItem> allMatchesQuery = em.createQuery("SELECT u FROM " + getEntityClass().getName()
					+ " u WHERE u.order = :order", getEntityClass());
			allMatchesQuery.setParameter("order", order);
			return allMatchesQuery.getResultList();
		});
		if (entities == null) {
			return new ArrayList<PersistenceOrderItem>();
		}
		return entities;
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
