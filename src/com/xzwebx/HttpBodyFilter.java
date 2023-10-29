package com.xzwebx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.regex.*;

class FiledCfg {
    public String fieldUrl;
    public String fieldType;
    public JSONArray rules;
    public String ifMust;
    public String keyType;
}
public class HttpBodyFilter implements Filter {
    private static HttpBodyFilter uniqueInstance = null;
    private Map moduleMap;
    private Map apiMap;
    private Map fieldMap;

    private HttpBodyFilter() {
    }

    public static synchronized HttpBodyFilter getInstance() {
        if (uniqueInstance == null) {
            uniqueInstance = new HttpBodyFilter();
        }
        return uniqueInstance;
    }

    public void setModuleMap(Map moduleMap) {
        this.moduleMap = moduleMap;
    }

    public void setApiMap(Map apiMap) {
        this.apiMap = apiMap;
    }

    public void setFieldMap(Map fieldMap) {
        this.fieldMap = fieldMap;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        servletResponse.setCharacterEncoding("UTF-8");
        servletResponse.setContentType("application/json; charset=utf-8");
        RequestWrapper req = new RequestWrapper((HttpServletRequest)servletRequest);
        String[] baseUrlList = req.getRequestURI().split("/");
        String subUri = baseUrlList[baseUrlList.length - 1];
        String baseUrl = "";
        for (int i = 0; i < baseUrlList.length - 1; i++) {
            if (!Objects.equals(baseUrlList[i], "")) {
                baseUrl += "/" + baseUrlList[i];
            }
        }
        Map module = JSONObject.parseObject(JSONObject.toJSONString(this.moduleMap.get(baseUrl)), Map.class);
        if (module == null || Objects.equals(module.get("uri"), "")) {
            try (PrintWriter writer = servletResponse.getWriter()) {
                writer.print(Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"ERR_URL"}, "[]"));
            } catch (IOException ignored) {
            }
            return;
        }

        Map moduleIdApiMap = JSONObject.parseObject(JSONObject.toJSONString(this.apiMap.get(String.valueOf(module.get("id")))), Map.class);
        if (moduleIdApiMap == null || moduleIdApiMap.get(subUri) == null) {
            try (PrintWriter writer = servletResponse.getWriter()) {
                writer.print(Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"ERR_URL"}, "[]"));
            } catch (IOException ignored) {
            }
            return;
        }

        Map moduleSuburiApiMap = JSONObject.parseObject(JSONObject.toJSONString(moduleIdApiMap.get(subUri))).toJavaObject(Map.class);
        if (moduleSuburiApiMap == null || moduleSuburiApiMap.get(req.getMethod().toLowerCase()) == null) {
            try (PrintWriter writer = servletResponse.getWriter()) {
                writer.print(Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"ERR_URL"}, "[]"));
            } catch (IOException ignored) {
            }
            return;
        }

        Map api = JSONObject.parseObject(JSONObject.toJSONString(moduleSuburiApiMap.get(req.getMethod().toLowerCase()))).toJavaObject(Map.class);
        if (api == null) {
            try (PrintWriter writer = servletResponse.getWriter()) {
                writer.print(Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"ERR_URL"}, "[]"));
            } catch (IOException ignored) {
            }
            return;
        }

        if ((int)api.get("reqMsgId") == 0) {
            filterChain.doFilter(servletRequest,servletResponse);
        }

        String bodyStr = req.getBodyString();
        String retMsgData = cycleCheckParams(JSONObject.parseObject(JSONObject.toJSONString(this.fieldMap.get(String.valueOf(api.get("reqMsgId"))))).toJavaObject(Map.class), bodyStr);
        if (!retMsgData.isEmpty()) {
            try (PrintWriter writer = servletResponse.getWriter()) {
                writer.print(retMsgData);
            } catch (IOException ignored) {
            }
            return;
        }
        filterChain.doFilter(req,servletResponse);
    }

    public static String trimChar(String sourceStr, char trimStr) {
        int len = sourceStr.length();
        int st = 0;
        char[] val = sourceStr.toCharArray();

        while ((st < len) && (val[st] <= trimStr)) {
            st++;
        }
        while ((st < len) && (val[len - 1] <= trimStr)) {
            len--;
        }
        return ((st > 0) || (len < sourceStr.length())) ? sourceStr.substring(st, len) : sourceStr;
    }
    private String cycleCheckParams(Map<String, Object> msgFieldMap, Object data) {
        if (msgFieldMap == null || msgFieldMap.get("__FieldCfg") == null) {
            return "";
        }

        FiledCfg fCfgItem = JSONObject.parseObject(JSONObject.toJSONString(msgFieldMap.get("__FieldCfg"))).toJavaObject(FiledCfg.class);
        if (fCfgItem == null || Objects.equals(fCfgItem.ifMust, "")) {
            return "";
        }

        for (Map.Entry<String, Object> map : msgFieldMap.entrySet()) {
            if (Objects.equals(map.getKey(), "__FieldCfg")) {
                if (Objects.equals(fCfgItem.fieldUrl, "")) {
                    if (Objects.equals(fCfgItem.ifMust, "YES")) {
                        if (data == null) {
                            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_MSG_BODY"}, "{}");
                        }

                        if (Objects.equals(fCfgItem.fieldType, "OBJ")) {
                            Map m = JSONObject.parseObject((String) data);
                            if (m == null) {
                                return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_OBJ_BODY"}, "{}");
                            }

                            if (m.isEmpty()) {
                                return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_MSG_BODY"}, "{}");
                            }
                        }

                        if (Objects.equals(fCfgItem.fieldType, "LIST")) {
                            JSONArray l = JSONArray.parseArray((String) data);
                            if (l == null) {
                                return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_LIST_VALUE"}, "{}");
                            }

                            if (l.isEmpty()) {
                                return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_MSG_BODY"}, "{}");
                            }
                        }
                    }
                } else {
                    if (Objects.equals(fCfgItem.ifMust, "NO") && (data == null)) {
                        return "";
                    }
                    String retMsgData = "";
                    if (Objects.equals(fCfgItem.fieldType, "OBJ")) {
                        retMsgData = this.isObjOk(fCfgItem, data);
                    } else if (Objects.equals(fCfgItem.fieldType, "LIST")) {
                        retMsgData = this.isListOk(fCfgItem, data);
                    } else if (Objects.equals(fCfgItem.fieldType, "STR")) {
                        retMsgData = this.isStringOk(fCfgItem, data);
                    } else if (Objects.equals(fCfgItem.fieldType, "INT")) {
                        retMsgData = this.isIntOk(fCfgItem, data);
                    }

                    if (!Objects.equals(retMsgData, "")) {
                        return retMsgData;
                    }
                }
                continue;
            }

            if (Objects.equals(fCfgItem.fieldType, "LIST")) {
                Object object = JSON.parse((String) data);
                if (!(object instanceof JSONArray)) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_LIST_VALUE", fCfgItem.fieldUrl}, "{}");
                }

                Object[] list = new JSONArray[]{JSONArray.parseArray((String) data)};
                for (Object o : list) {
                    String retMsgData = this.cycleCheckParams((Map<String, Object>) map.getValue(), o);
                    if (!Objects.equals(retMsgData, "")) {
                        return retMsgData;
                    }
                }
            } else if (Objects.equals(fCfgItem.fieldType, "OBJ") && Objects.equals(fCfgItem.keyType, "VOBJ")) {
                Object object = JSON.parse((String) data);
                if (!(object instanceof JSONObject)) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_OBJ_VALUE", fCfgItem.fieldUrl}, "{}");
                }

                Map m = JSONObject.parseObject((String) data);
                for (Object o : m.entrySet()) {
                    String retMsgData = this.cycleCheckParams((Map<String, Object>) map.getValue(), o);
                    if (!Objects.equals(retMsgData, "")) {
                        return retMsgData;
                    }
                }
            } else {
                Object object = JSON.parse((String) data);
                if (!(object instanceof JSONObject)) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_OBJ_VALUE", fCfgItem.fieldUrl}, "{}");
                }

                Map m = JSONObject.parseObject((String) data);
                Object o = null;
                if (m.get(map.getKey()) != null) {
                    o = m.get(map.getKey());
                }
                String retMsgData = this.cycleCheckParams((Map<String, Object>) map.getValue(), o);
                if (!Objects.equals(retMsgData, "")) {
                    return retMsgData;
                }
            }
        }


        return "";
    }
    private String isIntOk(FiledCfg fCfgItem, Object obj) {
        if (obj == null) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_INT_FIELD", fCfgItem.fieldUrl}, "{}");
        }

        double paramValue;
        if (obj instanceof BigDecimal) {
            paramValue = ((BigDecimal) obj).doubleValue();
        } else if (obj instanceof Integer) {
            paramValue = Double.parseDouble(obj.toString());
        } else {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NOT_INT_VALUE", fCfgItem.fieldUrl}, "{}");
        }

        if (fCfgItem.rules.isEmpty()) {
            return "";
        }

        for (int i = 0; i < fCfgItem.rules.size(); i++) {
            JSONObject rule = fCfgItem.rules.getJSONObject(i);
            JSONArray exprVal = (JSONArray) rule.get("exprVal");
            if (exprVal.isEmpty()) {
                continue;
            }

            if (Objects.equals(String.valueOf(rule.get("checkType")), "RANGE")) {
                if (exprVal.get(0) instanceof JSONArray) {
                    boolean isPass = false;
                    for (Object o : exprVal) {
                        JSONArray subExprVal = (JSONArray) o;
                        if (paramValue >= Double.parseDouble(JSONObject.toJSONString(subExprVal.get(0))) && paramValue <= Double.parseDouble(JSONObject.toJSONString(subExprVal.get(1)))) {
                            isPass = true;
                            break;
                        }
                    }
                    if (!isPass) {
                        return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_INT_RANGE", fCfgItem.fieldUrl, exprVal.toString()}, "{}");
                    }
                } else {
                    if (paramValue < Double.parseDouble(JSONObject.toJSONString(exprVal.get(0))) || paramValue > Double.parseDouble(JSONObject.toJSONString(exprVal.get(1)))) {
                        return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_INT_RANGE", fCfgItem.fieldUrl, exprVal.toString()}, "{}");
                    }
                }
            }

            if (Objects.equals(String.valueOf(rule.get("checkType")), "ENU")) {
                boolean isPass = false;
                for (Object o : exprVal) {
                    if (Double.parseDouble(JSONObject.toJSONString(o)) == Double.parseDouble(JSONObject.toJSONString(paramValue))) {
                        isPass = true;
                        break;
                    }
                }
                if (!isPass) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_ENU_VALUE", fCfgItem.fieldUrl, exprVal.toString()}, "{}");
                }
            }

            if (Objects.equals(String.valueOf(rule.get("checkType")), "REGEX")) {
                boolean isPass = false;
                String exprValStr = exprVal.toString();
                for (Object o : exprVal) {
                    Pattern  pattern = (Pattern.compile( trimChar(String.valueOf(o), '/') ));
                    Matcher matcher = pattern.matcher(JSONObject.toJSONString(paramValue));
                    boolean isMatch = matcher.matches();
                    if (Objects.equals(String.valueOf(rule.get("matchType")), "AND")) {
                        if (!isMatch) {
                            exprValStr = String.valueOf(o);
                            break;
                        }
                    } else {
                        if (isMatch) {
                            isPass = true;
                            break;
                        }
                    }
                }
                if (!isPass) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_REGEX_VALUE", fCfgItem.fieldUrl, exprValStr}, "{}");
                }
            }
        }
        return "";
    }
    private String isStringOk(FiledCfg fCfgItem, Object obj) {
        if (obj == null) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_STR_FIELD", fCfgItem.fieldUrl}, "{}");
        }

        String paramValue;
        if (obj instanceof String) {
            paramValue = (String) obj;
        } else {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NOT_STR_VALUE", fCfgItem.fieldUrl}, "{}");
        }

        if (fCfgItem.rules.isEmpty()) {
            return "";
        }

        for (int i = 0; i < fCfgItem.rules.size(); i++) {
            JSONObject rule = fCfgItem.rules.getJSONObject(i);
            JSONArray exprVal = (JSONArray) rule.get("exprVal");
            if (exprVal.isEmpty()) {
                continue;
            }

            if (Objects.equals(String.valueOf(rule.get("checkType")), "RANGE")) {
                if (exprVal.get(0) instanceof JSONArray) {
                    boolean isPass = false;
                    for (Object o : exprVal) {
                        JSONArray subExprVal = (JSONArray) o;
                        if (paramValue.length() >= (int) subExprVal.get(0) && paramValue.length() <= (int) subExprVal.get(1)) {
                            isPass = true;
                            break;
                        }
                    }
                    if (!isPass) {
                        return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_STR_RANGE", fCfgItem.fieldUrl, exprVal.toString()}, "{}");
                    }
                } else {
                    if (paramValue.length() < (int)exprVal.get(0) || paramValue.length() > (int)exprVal.get(1)) {
                        return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_STR_RANGE", fCfgItem.fieldUrl, exprVal.toString()}, "{}");
                    }
                }
            }

            if (Objects.equals(String.valueOf(rule.get("checkType")), "ENU")) {
                boolean isPass = false;
                if ((int)rule.get("isCaseSensitive") == 1) {
                    for (Object o : exprVal) {
                        if (Objects.equals(o, paramValue)) {
                            isPass = true;
                            break;
                        }
                    }
                } else {
                    for (Object o : exprVal) {
                        if (Objects.equals(o.toString().toUpperCase(), paramValue.toUpperCase())) {
                            isPass = true;
                            break;
                        }
                    }
                }
                if (!isPass) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_ENU_VALUE", fCfgItem.fieldUrl, exprVal.toString()}, "{}");
                }
            }

            if (Objects.equals(String.valueOf(rule.get("checkType")), "REGEX")) {
                boolean isPass = false;
                String exprValStr = exprVal.toString();
                for (Object o : exprVal) {
                    Pattern  pattern = (Pattern.compile( trimChar(String.valueOf(o), '/') ));
                    Matcher matcher = pattern.matcher(paramValue);
                    boolean isMatch = matcher.matches();
                    if (Objects.equals(String.valueOf(rule.get("matchType")), "AND")) {
                        if (!isMatch) {
                            exprValStr = String.valueOf(o);
                            break;
                        }
                    } else {
                        if (isMatch) {
                            isPass = true;
                            break;
                        }
                    }
                }
                if (!isPass) {
                    return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_REGEX_VALUE", fCfgItem.fieldUrl, exprValStr}, "{}");
                }
            }
        }

        return "";
    }
    private String isObjOk(FiledCfg fCfgItem, Object obj) {
        if (obj == null) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_FIELD", fCfgItem.fieldUrl}, "{}");
        }

        Map m = JSONObject.parseObject((String) obj);
        if (m == null) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_OBJ_VALUE", fCfgItem.fieldUrl}, "{}");
        }

        if (m.isEmpty()) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_VALUE", fCfgItem.fieldUrl}, "{}");
        }

        return "";
    }

    private String isListOk(FiledCfg fCfgItem, Object obj) {
        if (obj == null) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_FIELD", fCfgItem.fieldUrl}, "{}");
        }

        JSONArray l = JSONArray.parseArray((String) obj);
        if (l == null) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"WRONG_LIST_VALUE", fCfgItem.fieldUrl}, "{}");
        }

        if (l.isEmpty()) {
            return Response.getInstance().MSG("SYSTEM_TIPS", new String[] {"NULL_VALUE", fCfgItem.fieldUrl}, "{}");
        }

        return "";
    }
    @Override
    public void destroy() {
    }
}


