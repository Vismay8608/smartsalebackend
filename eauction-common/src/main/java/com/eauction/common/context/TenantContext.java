package com.eauction.common.context;

public final class TenantContext {

    private static final ThreadLocal<Integer> CURRENT_TENANT = new InheritableThreadLocal<>();
    private static final ThreadLocal<String>  CURRENT_USERNAME = new InheritableThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_USER_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String>  CURRENT_USER_TYPE = new InheritableThreadLocal<>();
    private static final ThreadLocal<String>  TRACE_ID = new InheritableThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Integer tenantId)    { CURRENT_TENANT.set(tenantId); }
    public static Integer getTenantId()                  { return CURRENT_TENANT.get(); }

    public static void setUsername(String username)     { CURRENT_USERNAME.set(username); }
    public static String getUsername()                   { return CURRENT_USERNAME.get(); }

    public static void setUserId(Integer userId)        { CURRENT_USER_ID.set(userId); }
    public static Integer getUserId()                    { return CURRENT_USER_ID.get(); }

    public static void setUserType(String userType)     { CURRENT_USER_TYPE.set(userType); }
    public static String getUserType()                   { return CURRENT_USER_TYPE.get(); }

    public static void setTraceId(String traceId)       { TRACE_ID.set(traceId); }
    public static String getTraceId()                    { return TRACE_ID.get(); }

    public static boolean hasTenant()                    { return CURRENT_TENANT.get() != null; }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USERNAME.remove();
        CURRENT_USER_ID.remove();
        CURRENT_USER_TYPE.remove();
        TRACE_ID.remove();
    }
}
