package com.wellsetups.wellmetin.integration;

import com.wellsetups.wellmetin.api.ItemProvider;
import com.wellsetups.wellmetin.api.ItemReference;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.bukkit.inventory.ItemStack;

/** Cached handles to documented public item APIs; no lookups or reflection discovery during combat. */
final class ExternalItemProvider implements ItemProvider {
    private final MethodHandle lookup;
    private final MethodHandle build;
    private final MethodHandle identify;
    private final MethodHandle namespacedId;

    ExternalItemProvider(ClassLoader loader, String apiClass, String lookupMethod,
                         String buildMethod, String identifyMethod, String idMethod) throws ReflectiveOperationException {
        Class<?> api = Class.forName(apiClass, true, loader);
        Method lookupApi = api.getMethod(lookupMethod, String.class);
        MethodHandles.Lookup access = MethodHandles.publicLookup();
        lookup = access.unreflect(lookupApi);
        build = access.unreflect(lookupApi.getReturnType().getMethod(buildMethod));
        Method identifyApi = api.getMethod(identifyMethod, ItemStack.class);
        identify = access.unreflect(identifyApi);
        namespacedId = idMethod == null ? null : access.unreflect(identifyApi.getReturnType().getMethod(idMethod));
    }

    @Override
    public ItemStack create(ItemReference reference, int amount) {
        Object builder = invoke(lookup, reference.id());
        if (builder == null) {
            throw new IllegalArgumentException("custom item is not loaded: " + reference.id());
        }
        ItemStack stack = ((ItemStack) invoke(build, builder)).clone();
        stack.setAmount(amount);
        return stack;
    }

    @Override
    public boolean matches(ItemReference reference, ItemStack stack) {
        Object item = invoke(identify, stack);
        if (item == null) {
            return false;
        }
        Object id = namespacedId == null ? item : invoke(namespacedId, item);
        return reference.id().equals(id);
    }

    private static Object invoke(MethodHandle handle, Object argument) {
        try {
            return handle.invoke(argument);
        } catch (Error failure) {
            throw failure;
        } catch (Throwable failure) {
            // MethodHandle.invoke declares Throwable even when the public target has no checked exceptions.
            throw new IllegalArgumentException("custom item API failed", failure);
        }
    }
}
