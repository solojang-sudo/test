package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.BasketResult;
import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.Item;
import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.MyUUID;
import uk.ncl.CSC8016.jackbergus.slides.semaphores.scheduler.Pair;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RainforestShop {

    /// For correctly implementing the server, pelase consider that

    private final boolean isGlobalLock;
    private boolean supplierStopped;
    private Set<String> allowed_clients;
    public HashMap<UUID, String> UUID_to_user;
    private volatile HashMap<String, ProductMonitor> available_withdrawn_products;
    private HashMap<String, Double> productWithCost = new HashMap<>();
    private volatile Queue<String> currentEmptyItem;
    private Map<String, List<Transaction>> transactions;
    private Lock shoplock;
    private Condition supplierCondition;
//    private AtomicBoolean isSupplierAvailable;
//    private AtomicBoolean isSupplierStopped;


    public boolean isGlobalLock() {
        return isGlobalLock;
    }

    /**
     * Please replace this string with your student ID, so to ease the marking process
     * @return  Your student id!
     */
    public String studentId() {
        return "c2039123";
    }


    /**
     *
     * @param client_ids                Collection of registered client names that can set up the communication
     * @param available_products        Map associating each product name to its cost and the initial number of available items on the shop
     * @param isGlobalLock              Might be used (but not strictly required) To remark whether your solution uses a
     *                                  pessimistic transaction (isGlobalLock=true) or an optimistic opne (isGlobalLock=false)
     */
    public RainforestShop(Collection<String> client_ids,
                          Map<String, Pair<Double, Integer>> available_products,
                          boolean isGlobalLock) {
        supplierStopped = true;
        currentEmptyItem = new LinkedBlockingQueue<>();
        this.isGlobalLock = isGlobalLock;
        allowed_clients = new HashSet<>();
        if (client_ids != null) allowed_clients.addAll(client_ids);
        this.available_withdrawn_products = new HashMap<>();
        UUID_to_user = new HashMap<>();
        if (available_products != null) for (var x : available_products.entrySet()) {
            if (x.getKey().equals("@stop!")) continue;
            productWithCost.put(x.getKey(), x.getValue().key);
            var p = new ProductMonitor();
            for (int i = 0; i<x.getValue().value; i++) {
                p.addAvailableProduct(new Item(x.getKey(), x.getValue().key, MyUUID.next()));
            }
            this.available_withdrawn_products.put(x.getKey(), p);
        }
        // 自己的改动
        // 储存当前活动的交易
//        Map<String, Transaction> transactions = new HashMap<>();
        transactions = new HashMap<>();
        shoplock = new ReentrantLock();
        supplierCondition = shoplock.newCondition();
//        isSupplierAvailable = new AtomicBoolean(true);
//        isSupplierStopped = new AtomicBoolean(false);
    }

    /**
     * Performing an user log-in. To generate a transaction ID, please use the customary Java method
     * 
     * UUID uuid = UUID.randomUUID();
     * 
     * @param username      Username that wants to login
     *
     * @return A non-empty transaction if the user is logged in for the first time, and he hasn't other instances of itself running at the same time
     *         In all the other cases, thus including the ones where the user is not registered, this returns an empty transaction
     *
     */
    public Optional<Transaction> login(String username) {
        Optional<Transaction> result = Optional.empty();
        if (allowed_clients.contains(username)) {
            UUID uuid = UUID.randomUUID();
            UUID_to_user.put(uuid, username);
            result = Optional.of(new Transaction(this, username, uuid));
        }
        return result;
    }

    /**
     * This method should be accessible only to the transaction and not to the public!
     * Logs out the client iff. there was a transaction that was started with a given UUID and that was associated to
     * a given user
     *
     * @param transaction 交易对象
     * @return false if the transaction is null or whether that was not created by the system
     */
    boolean logout(Transaction transaction) {
        boolean result = false;
        // TODO: Implement the remaining part!
        // 加锁
        shoplock.lock();
        try {
            //检查交易是否为null
            if (transaction == null || transaction.getUuid() == null) {
                return false;
            }
            // 获取交易的UUID
            UUID transactionUuid = transaction.getUuid();
            String userId = null;
            for (Map.Entry<UUID, String> entry : UUID_to_user.entrySet()) {
                if (entry.getKey().equals(transactionUuid)) {
                    userId = entry.getValue();
                    break;
                }
            }

            if (userId != null) {
                List<Item> itemsInBasket = new ArrayList<>(transaction.getUnmutableBasket());
                for (Item item : itemsInBasket) {
                    ProductMonitor productMonitor = available_withdrawn_products.get(item.productName);
                    if (productMonitor != null) {
                        productMonitor.addAvailableProduct(item);
                    }
                }
                UUID_to_user.remove(transactionUuid);
                result = true;
            }
        } finally {
            // 解锁
            shoplock.unlock();
        }
        return result;
    }
//        //检查交易是否为null
//        if (transaction == null || transaction.getUuid() == null) {
//            return false;
//        }
//        // 获取交易的UUID
//        UUID transactionUuid = transaction.getUuid();
//
//        // 遍历UUID_to_user映射，查找相应的用户ID
//        String userId = null;
//        for (Map.Entry<UUID, String> entry : UUID_to_user.entrySet()) {
//            if (entry.getKey().equals(transactionUuid)) {
//                userId = entry.getValue();
//                break;
//            }
//        }
//
//        // 检查用户是否已登录
//        if (userId != null) {
//            // 实现注销逻辑
//            // 执行相应的注销操作
//            // 重新上架购物篮中的商品
//            List<Item> itemsInBasket = new ArrayList<>(transaction.getUnmutableBasket()); // 复制购物篮的内容到可变列表
//            for (Item item : itemsInBasket) {
//                // 根据商品名称将商品重新放回到实际/虚拟货架上
//                ProductMonitor productMonitor = available_withdrawn_products.get(item.productName);
//                if (productMonitor != null) {
//                    productMonitor.addAvailableProduct(item);
//                }
//            }
//
//            // 从UUID_to_user映射中移除相应的条目
//            UUID_to_user.remove(transactionUuid);
//
//            result = true;
//        }
//
//        // 自己写的
//        return result;
//    }

    /**
     * Lists all of the items that were not basketed and that are still on the shelf
     * 我们使用了List<String> ls = Collections.emptyList();作为初始值，并在需要时将其替换为ArrayList。
     * 这样，我们能够将可用商品名称添加到ls中，并最终返回该列表。
     * 请注意，由于Collections.emptyList()返回的列表是不可变的，因此我们不能直接向其中添加元素。
     * 因此，在需要添加元素时，我们需要将其替换为可变的ArrayList，并将可用商品名称添加到其中。
     * @param transaction 交易对象
     * @return 可用商品列表
     */
    List<String> getAvailableItems(Transaction transaction) {
        List<String> ls = Collections.emptyList();
        // TODO: Implement the remaining part!
        shoplock.lock(); // 加锁
        try {
            if (transaction == null || transaction.getUuid() == null) {
                return ls;
            }
            Set<String> purchasedItem = transaction.getUnmutableBasket().stream().map(Item::getProductName).collect(Collectors.toSet());
            for (Map.Entry<String, ProductMonitor> entry : available_withdrawn_products.entrySet()) {
                String productName = entry.getKey();
                ProductMonitor productMonitor = entry.getValue();
                if (!purchasedItem.contains(productName)) {
                    Set<String> availableProductNames = productMonitor.getAvailableItems();
                    if (ls.isEmpty()) {
                        ls = new ArrayList<>(availableProductNames);
                    } else {
                        ls.addAll(availableProductNames);
                    }
                }
            }
        } finally {
            shoplock.unlock(); // 解锁
        }
        return ls;
    }
//        if (transaction == null || transaction.getUuid() == null) {
//            return ls; // 返回空的列表ls
//        }
//        //获取交易中已经购买的商品的名称集合
//        Set<String> purchasedItem = transaction.getUnmutableBasket().stream().map(Item::getProductName).collect(Collectors.toSet());
//        //遍历available.withdraw_products映射
//        for (Map.Entry<String, ProductMonitor> entry: available_withdrawn_products.entrySet()){
//            String productName = entry.getKey();
//            ProductMonitor productMonitor = entry.getValue();
//
//            //检查商品是否未被购买
//            if (!purchasedItem.contains(productName)) {
//                // 获取当前商品的可用上屏名称集合
//                Set<String> availableProductNames = productMonitor.getAvailableItems();
//                if (ls.isEmpty()) {
//                    //如果ls为空， 则创建一个新的ArrayList,并且赋值给ls
//                    ls = new ArrayList<>(availableProductNames);
//                }
//                else {
//                    //否则，将可用商品添加到ls中
//                    ls.addAll(availableProductNames);
//                }
//            }
//        }
//        return ls;
//    }

    /**
     * If a product can be basketed from the shelf, then a specific instance of the product on the shelf is returned
     * 我们首先检查交易对象和商品名称是否有效。然后，我们获取商品名称对应的商品监控对象，并检查该监控对象是否存在且是否有可用商品。
     * 如果有可用商品，我们从监控对象中获取一个可用商品，并将其放入购物篮中。
     * 最后，我们返回一个Optional，其中包含已放入购物篮的商品（如果有）或者一个空Optional。
     * @param transaction   User reference
     * @param name          Product name picked from the shelf
     * @return  Whether the item to be basketed is available or not
     */
    Optional<Item> basketProductByName(Transaction transaction, String name) {
        AtomicReference<Optional<Item>> result = new AtomicReference<>(Optional.empty());
        if (transaction.getSelf() == null || (transaction.getUuid() == null))
            return result.get(); // 返回空的Optional
        // TODO: Implement the remaining part!
        if (name == null || name.isEmpty()) {
            return result.get(); // 返回空的Optional
        }
        shoplock.lock();
        try {
            if (UUID_to_user.containsKey(transaction.getUuid())) {
                ProductMonitor productMonitor = available_withdrawn_products.get(name);
                if (productMonitor != null && !productMonitor.getAvailableItems().isEmpty()) {
                    Optional<Item> item = productMonitor.getAvailableItem();
                    if (item.isPresent()) {
                        transaction.basketProduct(item.get().getProductName());
                        result.set(item);
                    }
                }
            }
        } finally {
            shoplock.unlock();
        }
        return result.get(); // 返回Optional
    }
//        // 检查交易是否已经注销
//        if (UUID_to_user.containsKey(transaction.getUuid())){
//            // 获取可用商品的监控对象
//            ProductMonitor productMonitor = available_withdrawn_products.get(name);
//            // 检查商品监控对象是否存在且有可用的商品
//            if (productMonitor != null && !productMonitor.getAvailableItems().isEmpty()){
//                // 从商品监控对象中获取可用的商品
//                Optional<Item> item = productMonitor.getAvailableItem();
//                // 检查可用的商品是否存在
//                if (item.isPresent()) {
//                    // 将可用的商品放入购物篮
//                    transaction.basketProduct(item.get().getProductName());
//                    // 更新result未包含可用商品的Optional
//                    result.set(item);
//                }
//            }
//        }

//        return result.get(); // 返回Optional
//    }

    /**
     * If the current transaction has withdrawn one of the objects from the shelf and put it inside its basket,
     * then the transaction shall be also able to replace the object back where it was (on its shelf)
     * 我们首先检查交易对象和要放回货架的对象是否有效。然后，我们根据对象的商品名称获取相应的商品监控对象。
     * 如果商品监控对象存在，我们调用doShelf方法将对象放回原位置（即货架）。
     * 如果成功放回货架，我们还从交易的购物篮中移除该对象。最后，我们返回一个布尔值，指示对象是否已成功放回货架。
     *
     * @param transaction   Transaction that basketed the object
     * @param object        Object to be reshelved
     * @return  Returns true if the object existed before and if that was basketed by the current thread, returns false otherwise
     */
    boolean shelfProduct(Transaction transaction, Item object) {
        boolean result = false;
        if (transaction.getSelf() == null || (transaction.getUuid() == null)) return false;
        // TODO: Implement the remaining part!
        shoplock.lock(); // 加锁
        try {
            if (UUID_to_user.containsKey(transaction.getUuid())) {
                if (transaction.getUnmutableBasket().remove(object)) {
                    ProductMonitor productMonitor = available_withdrawn_products.get(object.getProductName());
                    if (productMonitor != null) {
                        result = productMonitor.doShelf(object);
                    }
                }
            }
        } finally {
            shoplock.unlock();
        }
        return result;
    }
//        // 检查交易是否已经注销
//        if (UUID_to_user.containsKey(transaction.getUuid())){
//            // 检查交易是否为null或者是否没有UUID
//            if (transaction == null || transaction.getUuid() == null) {
//                return false;
//            }
//
//            // 检查要放回货架的对象是否为null
//            if (object == null) {
//                return false;
//            }
//
//            // 获取商品的名称
//            String productName = object.getProductName();
//            ProductMonitor productMonitor = available_withdrawn_products.get(productName);
//
//            if (productMonitor != null) {
//                result = productMonitor.doShelf(object);
//
//                // 如果成功放回了货架，就从购物篮中移除对象
//                if (result) {
//                    transaction.getUnmutableBasket().remove(object);
//                }
//            }
//        }
//
//        return result;
//    }

    /**
     * Stops the food supplier by sending a specific message. Please observe that no product shall be named @stop!\
     * 添加了synchronized块来确保对currentEmptyItem的访问是线程安全的。
     * 通过synchronized (currentEmptyItem),确保在修改currentEmptyItem时只有一个线程可以访问它。
     * 然后，我在添加完"@stop!"之后，调用notifyAll()方法来通知等待currentEmptyItem的其他线程。
     */
    public void stopSupplier() {
        // TODO: Provide a correct concurrent implementation!
        shoplock.lock();
        try {
            currentEmptyItem.add("@stop!");
            supplierStopped = true;
            supplierCondition.signalAll(); // 唤醒等待的线程
        } finally {
            shoplock.unlock();
        }
    }
//        synchronized (currentEmptyItem) {
//            currentEmptyItem.add("@stop!");
//            currentEmptyItem.notifyAll();
//        }
//    }

    /**
     * The supplier acknowledges that it was stopped, and updates its internal state. The monitor also receives confirmation
     * 使用synchronized (this)来确保在修改supplierStopped和stopped时只有一个线程可以访问它们
     * 使用notifyAll()方法通知等待的线程
     * @param stopped   Boolean variable from the supplier
     */
    public void supplierStopped(AtomicBoolean stopped) {
        // TODO: Provide a correct concurrent implementation!
        shoplock.lock();
        try {
            supplierStopped = true;
            stopped.set(true);
            supplierCondition.signalAll(); // 唤醒等待的线程
        } finally {
            shoplock.unlock();
        }
    }
//        synchronized (this) {
//            supplierStopped = true;
//            stopped.set(true);
//            notifyAll();
//        }
//    }

    /**
     * The supplier invokes this method when it needs to know that a new product shall be made ready available.
     * 这个方法是用于供应商获取下一个缺货的产品。
     * 根据代码注释，它应该是一个阻塞方法，如果currentEmptyItem为空，那么它应该等待，直到currentEmptyItem中至少有一个元素。
     * 我们使用了synchronized关键字来获取currentEmptyItem对象的锁。
     * 然后，我们使用while循环来检查currentEmptyItem是否为空，如果为空，则调用wait()方法进行等待。
     * 当有其他线程调用notify()或notifyAll()方法唤醒了这个线程时，它会继续执行，直到currentEmptyItem中至少有一个元素。
     * 然后，我们从currentEmptyItem中移除并返回第一个元素。
     * 请注意，我还处理了可能抛出的InterruptedException异常
     * This method should be blocking (if currentEmptyItem is empty, then this should wait until currentEmptyItem
     * contains at least one element and, in that occasion, then returns the first element being available)
     * @return
     */
    public String getNextMissingItem() {
        // TODO: Provide a correct concurrent implementation!
        shoplock.lock();
        try {
            while (currentEmptyItem.isEmpty()) {
                try {
                    supplierCondition.await(); // 等待供应商补货
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return currentEmptyItem.remove();
        } finally {
            shoplock.unlock();
        }
    }
//        // 提供正确的并发实现
//        synchronized (currentEmptyItem) {
//            // 设置 supplierStoped 为 false, 表示供应没有停止
//            supplierStopped = false;
//            // 当 currentEmptyItem 为空时，进入循环等待
//            while (currentEmptyItem.isEmpty()){
//                try {
//                    // 使用 wait（）方法使线程等待，直到有其他线程调用notify（）或者 notifyAll（）来唤醒它
//                    currentEmptyItem.wait();
//                } catch (InterruptedException e) {
//                    // 中断处理异常
//                    e.printStackTrace();
//                }
//            }
//            // 从 currentEmptyItem 中移除并返回第一个可用的产品名称
//            return currentEmptyItem.remove();
//        }
//    }


    /**
     * This method is invoked by the Supplier to refurbrish the shop of n products of a given time (current item)
     * @param n                 Number of elements to be placed
     * @param currentItem       Type of elements to be placed
     */
    public void refurbishWithItems(int n, String currentItem) {
        // Note: this part of the implementation is completely correct!
        Double cost = productWithCost.get(currentItem);
        if (cost == null) return;
        for (int i = 0; i<n; i++) {
            available_withdrawn_products.get(currentItem).addAvailableProduct(new Item(currentItem, cost, MyUUID.next()));
        }
    }

    /**
     * This operation purchases all the elements available on the basket
     * @param transaction               Transaction containing the current withdrawn elements from the shelf (and therefore basketed)
     * @param total_available_money     How much money can the client spend at maximum
     * @return
     */
    public BasketResult basketCheckout(Transaction transaction, double total_available_money) {
        // Note: this part of the implementation is completely correct!
        BasketResult result = null;
        if (UUID_to_user.getOrDefault(transaction.getUuid(), "").equals(transaction.getUsername())) {
            var b = transaction.getUnmutableBasket();
            double total_cost = (0.0);
            List<Item> currentlyPurchasable = new ArrayList<>();
            List<Item> currentlyUnavailable = new ArrayList<>();
            for (Map.Entry<String, List<Item>> entry : b.stream().collect(Collectors.groupingBy(x -> x.productName)).entrySet()) {
                String k = entry.getKey();
                List<Item> v = entry.getValue();
                total_cost += available_withdrawn_products.get(k).updatePurchase(productWithCost.get(k), v, currentlyPurchasable, currentlyUnavailable);
            }
            if ((total_cost > total_available_money)) {
                for (Map.Entry<String, List<Item>> entry : b.stream().collect(Collectors.groupingBy(x -> x.productName)).entrySet()) {
                    String k = entry.getKey();
                    List<Item> v = entry.getValue();
                    available_withdrawn_products.get(k).makeAvailable(v);
                }
                currentlyUnavailable.clear();
                currentlyPurchasable.clear();
                total_cost = (0.0);
            } else {
                Set<String> s = new HashSet<>();
                for (Map.Entry<String, List<Item>> entry : b.stream().collect(Collectors.groupingBy(x -> x.productName)).entrySet()) {
                    String k = entry.getKey();
                    List<Item> v = entry.getValue();
                    if (available_withdrawn_products.get(k).completelyRemove(v))
                        s.add(k);
                }
                currentEmptyItem.addAll(s);
            }
            result = new BasketResult(currentlyPurchasable, currentlyUnavailable, total_available_money, total_cost, total_available_money- total_cost);
        }
        return result;
    }
}
