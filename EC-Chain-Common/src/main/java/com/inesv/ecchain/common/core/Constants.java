package com.inesv.ecchain.common.core;

import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.PropertiesUtil;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.TimeZone;

public final class Constants {
    public static final boolean EC_CORRECT_INVALID_FEES = PropertiesUtil.getKeyForBoolean("ec.correctInvalidFees");
    public static final int EC_MAX_MONITORS = PropertiesUtil.getKeyForInt("ec.maxNumberOfMonitors", 0);
    public static final boolean TRIM_DERIVED_TABLES = PropertiesUtil.getKeyForBoolean("ec.trimDerivedTables");
    public static final int DEFAULT_NUMBER_OF_FORK_CONFIRMATIONS = PropertiesUtil.getKeyForInt("ec.numberOfForkConfirmations", 0);
    public static final boolean SIMULATE_ENDLESS_DOWNLOAD = PropertiesUtil.getKeyForBoolean("ec.SIMULATE_ENDLESS_DOWNLOAD");
    public static final boolean IS_OFFLINE = PropertiesUtil.getKeyForBoolean("ec.isOffline");
    public static final boolean IS_LIGHT_CLIENT = PropertiesUtil.getKeyForBoolean("ec.isLightClient");
    public static final boolean USE_PROXY = System.getProperty("socksProxyHost") != null || System.getProperty("http.proxyHost") != null;
    public static final boolean HIDE_ERROR_DETAILS = PropertiesUtil.getKeyForBoolean("ec.hideErrorDetails");
    public static final boolean ENABLE_TRANSACTION_REBROADCASTING = PropertiesUtil.getKeyForBoolean("ec.enableTransactionRebroadcasting");
    public static final boolean TEST_UNCONFIRMED_TRANSACTIONS = PropertiesUtil.getKeyForBoolean("ec.TEST_UNCONFIRMED_TRANSACTIONS");
    public static final int MAX_ROLLBACK = Math.max(PropertiesUtil.getKeyForInt("ec.maxRollback", 0), 720);
    public static final int FORGING_DELAY = PropertiesUtil.getKeyForInt("ec.forgingDelay", 0);
    public static final int FORGING_SPEEDUP = PropertiesUtil.getKeyForInt("ec.forgingSpeedup", 0);
    public static final boolean INCLUDE_EXPIRED_PRUNABLE = PropertiesUtil.getKeyForBoolean("ec.includeExpiredPrunable");
    public static final int MAX_PRUNABLE_LIFETIME = PropertiesUtil.getKeyForInt("ec.maxPrunableLifetime", 0);
    public static final String EC_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    public static final String ALLOWED_CURRENCY_CODE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String EC_VERSION = "1.0.0";
    public static final String EC_APPLICATION = "NRS";
    public static final long EC_EPOCH_BEGINNING;
    public static final int EC_MAX_REQUEST_SIZE = 1024 * 1024;
    public static final int EC_MAX_RESPONSE_SIZE = 1024 * 1024;
    public static final int EC_MAX_MESSAGE_SIZE = 10 * 1024 * 1024;
    public static final int EC_LOGGING_MASK_EXCEPTIONS = 1;
    public static final int EC_LOGGING_MASK_NON200_RESPONSES = 2;
    public static final int EC_LOGGING_MASK_200_RESPONSES = 4;
    public static final int EC_MIN_COMPRESS_SIZE = 256;
    public static final int EC_MAX_VERSION_LENGTH = 10;
    public static final int EC_MAX_APPLICATION_LENGTH = 20;
    public static final int EC_MAX_PLATFORM_LENGTH = 30;
    public static final int EC_MAX_ANNOUNCED_ADDRESS_LENGTH = 100;
    public static final int EC_DEFAULT_PEER_PORT = 7874;
    public static final int EC_TESTNET_PEER_PORT = 6874;
    public static final int SEND_TRANSACTIONS_BATCH_SIZE = 10;
    public static final long EC_MIN_FUND_AMOUNT = 1;
    public static final long EC_MIN_FUND_THRESHOLD = 1;
    public static final int EC_MIN_FUND_INTERVAL = 10;
    public static final int EC_MAX_NUMBER_OF_TRANSACTIONS = 255;
    public static final int EC_MIN_TRANSACTION_SIZE = 176;
    public static final int EC_MAX_PAYLOAD_LENGTH = EC_MAX_NUMBER_OF_TRANSACTIONS * EC_MIN_TRANSACTION_SIZE;
    public static final long MAX_BALANCE_EC = 1000000000;
    public static final long ONE_EC = 100000000;
    public static final long EC_MAX_BALANCE_NQT = MAX_BALANCE_EC * ONE_EC;
    public static final long EC_INITIAL_BASE_TARGET = 153722867;
    public static final long EC_MAX_BASE_TARGET = MAX_BALANCE_EC * EC_INITIAL_BASE_TARGET;
    public static final long EC_MAX_BASE_TARGET_2 = EC_INITIAL_BASE_TARGET * 50;
    public static final long EC_MIN_BASE_TARGET = EC_INITIAL_BASE_TARGET * 9 / 10;
    public static final int EC_MIN_BLOCKTIME_LIMIT = 53;
    public static final int EC_MAX_BLOCKTIME_LIMIT = 67;
    public static final int EC_BASE_TARGET_GAMMA = 64;
    public static final int EC_GUARANTEED_BALANCE_CONFIRMATIONS = 1440;
    public static final int EC_LEASING_DELAY = 1440;
    public static final long EC_MIN_FORGING_BALANCE_NQT = 1000 * ONE_EC;
    public static final int EC_MAX_TIMEDRIFT = 15;
    public static final byte EC_MAX_PHASING_VOTE_TRANSACTIONS = 10;
    public static final byte EC_MAX_PHASING_WHITELIST_SIZE = 10;
    public static final byte EC_MAX_PHASING_LINKED_TRANSACTIONS = 10;
    public static final int EC_MAX_PHASING_DURATION = 14 * 1440;
    public static final int EC_MAX_PHASING_REVEALED_SECRET_LENGTH = 100;
    public static final int EC_MAX_ALIAS_URI_LENGTH = 1000;
    public static final int EC_MAX_ALIAS_LENGTH = 100;
    public static final int EC_MAX_ARBITRARY_MESSAGE_LENGTH = 160;
    public static final int EC_MAX_ENCRYPTED_MESSAGE_LENGTH = 160 + 16;
    public static final int EC_MAX_PRUNABLE_MESSAGE_LENGTH = 42 * 1024;
    public static final int EC_MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH = 42 * 1024;
    public static final int EC_MIN_PRUNABLE_LIFETIME = 14 * 1440 * 60;
    public static final boolean EC_ENABLE_PRUNING = MAX_PRUNABLE_LIFETIME >= 0;
    public static final int EC_MAX_PRUNABLE_LIFETIME = EC_ENABLE_PRUNING ? Math.max(MAX_PRUNABLE_LIFETIME, EC_MIN_PRUNABLE_LIFETIME) : Integer.MAX_VALUE;
    public static final int EC_MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int EC_MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;
    public static final int EC_MAX_ACCOUNT_PROPERTY_NAME_LENGTH = 32;
    public static final int EC_MAX_ACCOUNT_PROPERTY_VALUE_LENGTH = 160;
    public static final long EC_MAX_ASSET_QUANTITY_QNT = 1000000000L * 100000000L;
    public static final int EC_MIN_ASSET_NAME_LENGTH = 3;
    public static final int EC_MAX_ASSET_NAME_LENGTH = 10;
    public static final int EC_MAX_ASSET_DESCRIPTION_LENGTH = 1000;
    public static final int EC_MAX_SINGLETON_ASSET_DESCRIPTION_LENGTH = 160;
    public static final int EC_MAX_ASSET_TRANSFER_COMMENT_LENGTH = 1000;
    public static final int EC_MAX_DIVIDEND_PAYMENT_ROLLBACK = 1441;
    public static final int EC_MAX_POLL_NAME_LENGTH = 100;
    public static final int EC_MAX_POLL_DESCRIPTION_LENGTH = 1000;
    public static final int EC_MAX_POLL_OPTION_LENGTH = 100;
    public static final int EC_MAX_POLL_OPTION_COUNT = 100;
    public static final int EC_MAX_POLL_DURATION = 14 * 1440;
    public static final byte EC_MIN_VOTE_VALUE = -92;
    public static final byte EC_MAX_VOTE_VALUE = 92;
    public static final byte EC_NO_VOTE_VALUE = Byte.MIN_VALUE;
    public static final int EC_MAX_DGS_LISTING_QUANTITY = 1000000000;
    public static final int EC_MAX_DGS_LISTING_NAME_LENGTH = 100;
    public static final int EC_MAX_DGS_LISTING_DESCRIPTION_LENGTH = 1000;
    public static final int EC_MAX_DGS_LISTING_TAGS_LENGTH = 100;
    public static final int EC_MAX_DGS_GOODS_LENGTH = 1000;
    public static final int EC_MAX_HUB_ANNOUNCEMENT_URIS = 100;
    public static final int EC_MAX_HUB_ANNOUNCEMENT_URI_LENGTH = 1000;
    public static final long EC_MIN_HUB_EFFECTIVE_BALANCE = 100000;
    public static final int EC_MIN_CURRENCY_NAME_LENGTH = 3;
    public static final int EC_MAX_CURRENCY_NAME_LENGTH = 10;
    public static final int EC_MIN_CURRENCY_CODE_LENGTH = 3;
    public static final int EC_MAX_CURRENCY_CODE_LENGTH = 5;
    public static final int EC_MAX_CURRENCY_DESCRIPTION_LENGTH = 1000;
    public static final long EC_MAX_CURRENCY_TOTAL_SUPPLY = 1000000000L * 100000000L;
    public static final int EC_MAX_MINTING_RATIO = 10000;
    public static final byte EC_MIN_NUMBER_OF_SHUFFLING_PARTICIPANTS = 3;
    public static final byte EC_MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS = 30;
    public static final short EC_MAX_SHUFFLING_REGISTRATION_PERIOD = (short) 1440 * 7;
    public static final short EC_SHUFFLING_PROCESSING_DEADLINE = (short) 100;
    public static final int EC_MAX_TAGGED_DATA_NAME_LENGTH = 100;
    public static final int EC_MAX_TAGGED_DATA_DESCRIPTION_LENGTH = 1000;
    public static final int EC_MAX_TAGGED_DATA_TAGS_LENGTH = 100;
    public static final int EC_MAX_TAGGED_DATA_TYPE_LENGTH = 100;
    public static final int EC_MAX_TAGGED_DATA_CHANNEL_LENGTH = 100;
    public static final int EC_MAX_TAGGED_DATA_FILENAME_LENGTH = 100;
    public static final int EC_MAX_TAGGED_DATA_DATA_LENGTH = 42 * 1024;
    public static final int EC_TRANSPARENT_FORGING_BLOCK = 0;
    public static final int EC_TRANSPARENT_FORGING_BLOCK_1 = 51000;
    public static final int EC_TRANSPARENT_FORGING_BLOCK_2 = 67000;
    public static final int EC_TRANSPARENT_FORGING_BLOCK_3 = 130000;
    public static final int EC_TRANSPARENT_FORGING_BLOCK_4 = Integer.MAX_VALUE;
    public static final int EC_TRANSPARENT_FORGING_BLOCK_5 = 215000;
    public static final int EC_NQT_BLOCK = 0;
    public static final int EC_REFERENCED_TRANSACTION_FULL_HASH_BLOCK = 140000;
    public static final int EC_REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP = 15134204;
    public static final int EC_MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 1440 * 60;
    public static final int EC_DIGITAL_GOODS_STORE_BLOCK = 0;
    public static final int EC_MONETARY_SYSTEM_BLOCK = 330000;
    public static final int EC_PHASING_BLOCK = -1;
    public static final int EC_SHUFFLING_BLOCK = 621000;
    public static final int EC_FXT_BLOCK = 1000000;
    public static final int DISTRIBUTION_END = EC_FXT_BLOCK;
    public static final int DISTRIBUTION_START = DISTRIBUTION_END - 90 * 1440;
    public static final int EC_LAST_CHECKSUM_BLOCK = 0;
    public static final int EC_LAST_KNOWN_BLOCK = -1;
    public static final int[] EC_MIN_VERSION = new int[]{1, 0, 0};
    public static final int[] EC_MIN_PROXY_VERSION = new int[]{1, 0, 0};
    public static final long EC_UNCONFIRMED_POOL_DEPOSIT_NQT = 100 * ONE_EC;
    public static final long EC_SHUFFLING_DEPOSIT_NQT = 1000 * ONE_EC;
    public static final int TRIM_KEEP = PropertiesUtil.getKeyForInt("ec.ledgerTrimKeep", 30000);
    public static final int MAX_FORGERS = PropertiesUtil.getKeyForInt("ec.maxNumberOfForgers", 0);
    public static final int DISTRIBUTION_FREQUENCY = 720;
    public static final int DISTRIBUTION_STEP = 60;
    public static final BigInteger BALANCE_DIVIDER = BigInteger.valueOf(10000L * (DISTRIBUTION_END - DISTRIBUTION_START) / DISTRIBUTION_STEP);
    public static final long FXT_ASSET_ID = Long.parseUnsignedLong("12422608354438203866");//TODO
    public static final long FXT_ISSUER_ID = Convert.parseAccountId("EC-FQ28-G9SQ-BG8M-6V6QH");//TODO
    public static final String logAccount = PropertiesUtil.getKeyForString("ec.logFxtBalance", null);
    public static final long logAccountId = Convert.parseAccountId(logAccount);
    public static final String fxtJsonFile =  "fxt.json";
    public static final boolean hasSnapshot = ClassLoader.getSystemResource(fxtJsonFile) != null;
    public static final String appendixName = "PrunablePlainMessage";
    public static final int MAX_SHUFFLERS = PropertiesUtil.getKeyForInt("ec.maxNumberOfShufflers", 0);
    public static final boolean DELETE_FINISHED = PropertiesUtil.getKeyForBoolean("ec.deleteFinishedShufflings");
    public static final byte TYPE_MONETARY_SYSTEM = 5;
    public static final byte TYPE_SHUFFLING = 7;
    public static final byte TYPE_PAYMENT = 0;
    public static final byte TYPE_MESSAGING = 1;
    public static final byte TYPE_COLORED_COINS = 2;
    public static final byte TYPE_DIGITAL_GOODS = 3;
    public static final byte TYPE_ACCOUNT_CONTROL = 4;
    public static final byte TYPE_DATA = 6;
    public static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
    public static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    public static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
    public static final byte SUBTYPE_MESSAGING_POLL_CREATION = 2;
    public static final byte SUBTYPE_MESSAGING_VOTE_CASTING = 3;
    public static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 4;
    public static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
    public static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
    public static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;
    public static final byte SUBTYPE_MESSAGING_ALIAS_DELETE = 8;
    public static final byte SUBTYPE_MESSAGING_PHASING_VOTE_CASTING = 9;
    public static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY = 10;
    public static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE = 11;
    public static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
    public static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
    public static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
    public static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
    public static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
    public static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;
    public static final byte SUBTYPE_COLORED_COINS_DIVIDEND_PAYMENT = 6;
    public static final byte SUBTYPE_COLORED_COINS_ASSET_DELETE = 7;
    public static final byte SUBTYPE_DIGITAL_GOODS_LISTING = 0;
    public static final byte SUBTYPE_DIGITAL_GOODS_DELISTING = 1;
    public static final byte SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE = 2;
    public static final byte SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE = 3;
    public static final byte SUBTYPE_DIGITAL_GOODS_PURCHASE = 4;
    public static final byte SUBTYPE_DIGITAL_GOODS_DELIVERY = 5;
    public static final byte SUBTYPE_DIGITAL_GOODS_FEEDBACK = 6;
    public static final byte SUBTYPE_DIGITAL_GOODS_REFUND = 7;
    public static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    public static final byte SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY = 1;
    public static final byte SUBTYPE_DATA_TAGGED_DATA_UPLOAD = 0;
    public static final byte SUBTYPE_DATA_TAGGED_DATA_EXTEND = 1;
    public static final String RUNTIME_MODE_ARG = "ec.runtime.mode";
    public static final String DIRPROVIDER_ARG = "ec.runtime.dirProvider";
    public static final String OSNAME = System.getProperty("os.name").toLowerCase();
    public static final long TEMP =PropertiesUtil.getKeyForInt("ec.statementLogThreshold", 0);
    public static final long TX_INTERVAL = TEMP != 0 ? TEMP * 60 * 1000 : 15 * 60 * 1000;
    public static final long TX_THRESHOLD = TEMP != 0 ? TEMP : 5000;
    public static final long STMT_THRESHOLD = TEMP != 0 ? TEMP : 1000;
    public static final int PEER_WEB_SOCKET_FLAG_COMPRESSED = 1;
    public static final int PEER_WEB_SOCKET_VERSION = 1;
    public static final boolean ENFORCE_POST = PropertiesUtil.getKeyForBoolean("ec.uiServerEnforcePOST");
    public static final int BLACKLISTING_PERIOD = PropertiesUtil.getKeyForInt("ec.apiProxyBlacklistingPeriod", 0) / 1000;
    public static final String FORCED_SERVER_URL = PropertiesUtil.getKeyForString("ec.forceAPIProxyServerURL", "");
    public static final int PROXY_IDLE_TIMEOUT_DELTA = 5000;
    public static final boolean API_SERVLET_ENFORCE_POST = PropertiesUtil.getKeyForBoolean("ec.apiServerEnforcePOST");
    public static final int MAX_EVENT_USERS = PropertiesUtil.getKeyForInt("ec.apiMaxEventUsers", 0);
    public static final int EVENT_TIMEOUT = Math.max(PropertiesUtil.getKeyForInt("ec.apiEventTimeout", 0), 15);
    public static final Path PLUGINS_HOME = Paths.get("./html/www/plugins");

    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2018);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 18);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        EC_EPOCH_BEGINNING = calendar.getTimeInMillis();
    }

    /*static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2018);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 18);
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 8);
        calendar.set(Calendar.SECOND, 8);
        calendar.set(Calendar.MILLISECOND, 8);
        EC_EPOCH_BEGINNING = calendar.getTimeInMillis();
    }*/
}