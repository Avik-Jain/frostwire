/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.offers.play;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.frostwire.android.offers.Product;
import com.frostwire.android.offers.Products;
import com.frostwire.android.offers.StoreBase;
import com.frostwire.util.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.frostwire.android.offers.Products.toDays;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PlayStore extends StoreBase {

    private static final Logger LOG = Logger.getLogger(PlayStore.class);

    // Taken from: Google Play Developer Console -> Services & APIs
    // Base64-encoded RSA public key to include in your binary.
    private static final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn4zB2rCYz3oXs33iFIHagzwpca0AEvRYHyr2xOW9gGwBokU51LdIjzq5NOzj3++aa9vIvj/K9eFHCPxkXa5g2qjm1+lc+fJwIEA/hAnA4ZIee3KrD52kyTqfZfhEYGklzvarbo3WN2gcUzwvvsVP9e1UZqtoYgFDThttKaFUboqqt1424lp7C2da89WTgHNpUyykIwQ1zYR34YOQ23SFPesSx8Fmz/Nz2rAHBNuFy13OE2LWPK+kLfm8P+tUAOcDSlq0NuT/FkuGpvziPaOS5BVpvfiAjjnUNLfH7dEO5wh7RPAskcNhQH1ykp6RauZFryMJbbHUe6ydGRHzpRkRpwIDAQAB";

    private static final String INAPP_TYPE = "inapp";
    private static final String SUBS_TYPE = "subs";

    private BillingClient billingClient;
    private PurchasesUpdatedListener purchasesUpdatedListener;

    private Inventory inventory;
    private String lastSkuPurchased;

    private static final Object lock = new Object();
    private static PlayStore instance;

    @NonNull
    public static PlayStore getInstance(@NonNull Context context) {
        synchronized (lock) {
            if (instance == null) {
                instance = new PlayStore(context.getApplicationContext());
            }
            return instance;
        }
    }

    public PlayStore(Context context) {

        purchasesUpdatedListener = (responseCode, purchases) -> {
            if (purchases == null) {
                // could be the result of a call to endAsync
                LOG.info("onPurchasesUpdated() - purchases collection is null");
                return;
            }

            if (responseCode == BillingClient.BillingResponse.OK) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
                // TODO:
                //mBillingUpdatesListener.onPurchasesUpdated(mPurchases);
            } else if (responseCode == BillingClient.BillingResponse.USER_CANCELED) {
                LOG.info("onPurchasesUpdated() - user cancelled the purchase flow - skipping");
            } else {
                LOG.info("onPurchasesUpdated() got unknown resultCode: " + responseCode);
            }
        };

        billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .build();

        startServiceConnection(new Runnable() {
            @Override
            public void run() {
                // Notifying the listener that billing client is ready
//                mBillingUpdatesListener.onBillingClientSetupFinished();
//                // IAB is fully set up. Now, let's get an inventory of stuff we own.
//                Log.d(TAG, "Setup successful. Querying inventory.");
//                queryPurchases();
            }
        });
    }

    @Override
    public void refresh() {

    }

    @Override
    public void purchase(Activity activity, Product p) {

    }

    private void startServiceConnection(final Runnable executeOnSuccess) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@BillingClient.BillingResponse int billingResponseCode) {
                LOG.info("Setup finished. Response code: " + billingResponseCode);

//                if (billingResponseCode == BillingClient.BillingResponse.OK) {
//                    mIsServiceConnected = true;
//                    if (executeOnSuccess != null) {
//                        executeOnSuccess.run();
//                    }
//                }
//                mBillingClientResponseCode = billingResponseCode;
            }

            @Override
            public void onBillingServiceDisconnected() {
//                mIsServiceConnected = false;
            }
        });
    }

    private void handlePurchase(Purchase purchase) {
        if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
            LOG.warn("Got a purchase: " + purchase + "; but signature is bad. Skipping...");
            return;
        }

        LOG.warn("Got a verified purchase: " + purchase);

        String sku = purchase.getSku();
        lastSkuPurchased = sku;
        LOG.info("Purchased sku " + sku);

        if (inventory != null) {
            try {
                inventory.addPurchase(purchase);
                products = buildProducts(inventory);
                LOG.info("Inventory updated");
            } catch (Throwable e) {
                LOG.error("Error updating internal inventory after purchase", e);
            }
        }
    }

    private Map<String, Product> buildProducts(Inventory inventory) {
        if (inventory == null) {
            LOG.warn("Inventory is null, review your logic");
            return Collections.emptyMap();
        }

        Map<String, Product> m = new HashMap<>();

        // build each product, one by one, not magic here intentionally
        Product product;

        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_MONTH_SKU, INAPP_TYPE, inventory, toDays(Products.INAPP_DISABLE_ADS_1_MONTH_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU, INAPP_TYPE, inventory, toDays(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_YEAR_SKU, INAPP_TYPE, inventory, toDays(Products.INAPP_DISABLE_ADS_1_YEAR_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_MONTH_SKU, SUBS_TYPE, inventory, toDays(Products.SUBS_DISABLE_ADS_1_MONTH_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU, SUBS_TYPE, inventory, toDays(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_YEAR_SKU, SUBS_TYPE, inventory, toDays(Products.SUBS_DISABLE_ADS_1_YEAR_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        return m;
    }

    private Product buildDisableAds(final String sku, final String type, Inventory inventory, final int days) {
        final SkuDetails d = inventory.getSkuDetails(sku);
        Purchase p = inventory.getPurchase(sku);

        // see if product exists
        final boolean exists = d != null && d.getType().equals(type); // product exists in the play store

        final boolean subscription = type == SUBS_TYPE;
        final String title = exists ? d.getTitle() : "NA";
        final String description = exists ? d.getDescription() : "NA";
        final String price = exists ? d.getPrice() : "NA";
        final String currency = exists ? d.getPriceCurrencyCode() : "NA";

        // see if it the user has some conflicting sku purchase
        String[] disableAdsSku = new String[]{
                Products.INAPP_DISABLE_ADS_1_MONTH_SKU,
                Products.INAPP_DISABLE_ADS_6_MONTHS_SKU,
                Products.INAPP_DISABLE_ADS_1_YEAR_SKU,
                Products.SUBS_DISABLE_ADS_1_MONTH_SKU,
                Products.SUBS_DISABLE_ADS_6_MONTHS_SKU,
                Products.SUBS_DISABLE_ADS_1_YEAR_SKU
        };
        boolean conflict = false;
        for (int i = 0; !conflict && i < disableAdsSku.length; i++) {
            String s = disableAdsSku[i];
            if (s != sku && inventory.hasPurchase(s)) {
                conflict = true;
            }
        }

        // TODO: review this
        // see if product is purchased
        boolean purchased = p != null; // already purchased
        /*
        // see if time expired, then consume it
        if (p != null && type == INAPP_TYPE) {
            long time = TimeUnit.DAYS.toMillis(days);
            long now = System.currentTimeMillis();
            if (now - p.getPurchaseTime() > time) {
                try {
                    billingClient.consumeAsync(p, consumeFinishedListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    LOG.error("Error consuming purchase. Another async operation in progress.", e);
                } catch (Throwable e) {
                    LOG.error("Error consuming purchase", e);
                }
                purchased = false;
            }
        }*/

        final boolean available = exists && !conflict && !purchased;
        final long purchaseTime = purchased ? p.getPurchaseTime() : 0;

        return new Products.ProductBase(sku, subscription, title,
                description, price, currency, purchased, purchaseTime, available) {

            @Override
            public boolean enabled(String feature) {
                // only support disable ads feature
                if (feature != Products.DISABLE_ADS_FEATURE) {
                    return false;
                }

                // if available, then the user does not have it, then
                // the feature is not enabled
                if (available) {
                    return false;
                }

                // at this point, the user have it, if it's a subscription
                // then it is enabled
                if (type == SUBS_TYPE) {
                    return true;
                }

                long time = TimeUnit.DAYS.toMillis(days);
                long now = System.currentTimeMillis();
                return now - purchaseTime <= time;
            }
        };
    }

    private static boolean verifyValidSignature(String signedData, String signature) {
        try {
            return Security.verifyPurchase(base64EncodedPublicKey, signedData, signature);
        } catch (IOException e) {
            LOG.warn("Got an exception trying to validate a purchase: " + e);
            return false;
        }
    }
}
