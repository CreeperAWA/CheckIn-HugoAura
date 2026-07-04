import {ElMessage} from "element-plus";
import router from "@/router/index.js";

let waitingTasks = [];
const PermissionInfo = {
    permissions: ref({}),
    initialized: false,
    init: function (data) {
        this.initialized = true;
        this.permissions.value = data;
        if (waitingTasks !== null && waitingTasks !== undefined &&
            waitingTasks.length > 0) {
            for (const promise of waitingTasks) {
                promise.doAction();
            }
        }
        waitingTasks = null;
    },
    hasPermissionAsync: function (group, name) {
        let promiseResolve;
        const promise = new Promise(resolve => {
            promiseResolve = resolve;
        });
        promise.doAction = () => {
            promiseResolve(PermissionInfo.hasPermission(group, name));
        }
        if (PermissionInfo.initialized) promise.doAction();
        else waitingTasks.push(promise);
        return promise;
    },
    hasPermission: function (group, name) {
        // 支持两种调用方式：
        // 1. hasPermission('group', 'permission') - 传统方式
        // 2. hasPermission('permission') - 直接使用权限名称
        if (name === undefined) {
            // 直接使用权限名称的情况
            const permissionName = group;
            // 遍历所有权限组，查找是否存在该权限
            for (const groupName in PermissionInfo.permissions.value) {
                const permissions = PermissionInfo.permissions.value[groupName];
                if (permissions instanceof Array) {
                    const found = permissions.find(item => item.name === permissionName);
                    if (found) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            // 传统方式：指定权限组和权限名称
            const permissions1 = PermissionInfo.permissions.value[group];
            const regExp = new RegExp(name, "i");
            return permissions1 instanceof Array && Boolean(permissions1.find(item => regExp.test(item.name)));
        }
    },
    requirePageAccess: async function (permissionName, customMessage) {
        const permissions = Array.isArray(permissionName) ? permissionName : [permissionName];
        if (!this.initialized) {
            await this.waitingForInitialize();
        }
        const hasAny = permissions.some(p => this.hasPermission(p));
        if (!hasAny) {
            ElMessage({
                type: "error",
                message: customMessage || "无权限访问此页面"
            });
            router.push("/");
            return false;
        }
        return true;
    },
    waitingForInitialize: async function () {
        if (PermissionInfo.initialized) {
            return Promise.resolve();
        } else {
            let promiseResolve;
            const promise = new Promise(resolve => {
                promiseResolve = resolve;
            });
            promise.doAction = () => {
                promiseResolve();
            }
            waitingTasks.push(promise);
            return promise;
        }
    }
}
export default PermissionInfo;