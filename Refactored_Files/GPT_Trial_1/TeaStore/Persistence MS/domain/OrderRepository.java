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
		order.setAddressName(entity.getAddressName());
		order.setAddress1(entity.getAddress1());
		order.setAddress2(entity.getAddress2());
		order.setCreditCardCompany(entity.getCreditCardCompany());
		order.setCreditCardExpiryDate(entity.getCreditCardExpiryDate());
		order.setCreditCardNumber(entity.getCreditCardNumber());
		order.setTime(entity.getTime());
		order.setTotalPriceInCents(entity.getTotalPriceInCents());

		inTx(em -> {
			PersistenceUser user = em.find(PersistenceUser.class, entity.getUserId());
			if (user != null) {
				order.setUser(user);
				em.persist(order);
			} else {
				order.setId(-1L);
			}
		});
		return order.getId();
	}

	@Override
	public boolean updateEntity(long id, Order entity) {
		return inTx(em -> {
			PersistenceOrder order = em.find(getEntityClass(), id);
			if (order == null) {
				return false;
			}
			order.setAddressName(entity.getAddressName());
			order.setAddress1(entity.getAddress1());
			order.setAddress2(entity.getAddress2());
			order.setCreditCardCompany(entity.getCreditCardCompany());
			order.setCreditCardExpiryDate(entity.getCreditCardExpiryDate());
			order.setCreditCardNumber(entity.getCreditCardNumber());
			order.setTime(entity.getTime());
			order.setTotalPriceInCents(entity.getTotalPriceInCents());
			PersistenceUser user = em.find(PersistenceUser.class, entity.getUserId());
			if (user != null) {
				order.setUser(user);
			}
			return true;
		});
	}

	public List<PersistenceOrder> getAllEntitiesWithUser(long userId) {
		List<PersistenceOrder> entities = inTx(em -> {
			PersistenceUser user = em.find(PersistenceUser.class, userId);
			if (user == null) {
				return null;
			}
			TypedQuery<PersistenceOrder> allMatchesQuery = em.createQuery("SELECT u FROM " + getEntityClass().getName()
					+ " u WHERE u.user = :user", getEntityClass());
			allMatchesQuery.setParameter("user", user);
			return allMatchesQuery.getResultList();
		});
		if (entities == null) {
			return new ArrayList<PersistenceOrder>();
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
