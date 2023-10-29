package com.xzwebx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.Map;
import java.util.Objects;

public class Response {
    private static Response uniqueInstance = null;
    private Response() {
    }
    public static synchronized Response getInstance() {
        if (uniqueInstance == null) {
            uniqueInstance = new Response();
        }
        return uniqueInstance;
    }
    public void setResCodeMap(Map resCodeMap) {
        this.resCodeMap = resCodeMap;
    }
    public void setTipsMap(Map tipsMap) {
        this.tipsMap = tipsMap;
    }
    Map resCodeMap;
    Map tipsMap;
    private String getRstCode(String codeKey) {
        String code = "";
        Map resCode = JSONObject.parseObject(JSONObject.toJSONString(uniqueInstance.resCodeMap.get(codeKey)), Map.class);
        if (resCode.get("rstCode") != null && !Objects.equals(resCode.get("rstCode"), "")) {
            code = (String) resCode.get("rstCode");
        }
        return code;
    }
    private String GetMsgByStringParam(String message, String codeKey) {
        String msg = "";
        if (message == null || message.isEmpty()) {
            Map tips = JSONObject.parseObject(JSONObject.toJSONString(uniqueInstance.tipsMap.get(codeKey)), Map.class);
            if (tips != null && tips.get("tips") != null && !Objects.equals(tips.get("tips"), "")) {
                msg = (String) tips.get("tips");
            }
        } else {
            msg = message;
        }
        return msg;
    }
    public String MSG(String codeKey, String message, String data) {
        String code = this.getRstCode(codeKey);
        String msg = this.GetMsgByStringParam(message, codeKey);
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        if (data != null && !data.isEmpty()) {
            Object object = JSON.parse(data);
            if (object instanceof JSONObject) {
                json.put("data", JSONObject.parseObject(data));
            } else if (object instanceof JSONArray) {
                json.put("data", JSONObject.parseArray(data));
            }
        }
        return JSONObject.toJSONString(json);
    }
    public String MSG(String codeKey, String[] message, String data) {
        String code = this.getRstCode(codeKey);
        String msg = this.forMatMsg(message);
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        if (data != null && !data.isEmpty()) {
            Object object = JSON.parse(data);
            if (object instanceof JSONObject) {
                json.put("data", JSONObject.parseObject(data));
            } else if (object instanceof JSONArray) {
                json.put("data", JSONObject.parseArray(data));
            }
        }
        return JSONObject.toJSONString(json);
    }
    public String MSG(String codeKey, String[] message, Object data) {
        String code = this.getRstCode(codeKey);
        String msg = this.forMatMsg(message);
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        json.put("data", data);
        return JSONObject.toJSONString(json);
    }

    private String forMatMsg(String[] message) {
        String msg = "";
        if (message.length > 0) {
            Map tips = JSONObject.parseObject(JSONObject.toJSONString(uniqueInstance.tipsMap.get(message[0])), Map.class);
            if (tips != null && tips.get("tips") != null && !Objects.equals(tips.get("tips"), "")) {
                msg = (String) tips.get("tips");
                if (message.length > 1) {
                    String cutMsg = msg;
                    StringBuilder msgTemp = new StringBuilder();
                    for (int i = 1; i< message.length; i++) {
                        String tplStr = cutMsg.substring(0, cutMsg.length());
                        int idx = tplStr.indexOf('%');
                        if (idx == -1) {
                            return msg;
                        }

                        int eIdx = idx +2;
                        if (i >= message.length - 1) {
                            eIdx = cutMsg.length();
                        }
                        String tMsg = tplStr.substring(0, eIdx);
                        msgTemp.append(String.format(tMsg, message[i]));
                        cutMsg = cutMsg.substring(idx+2, cutMsg.length());
                    }
                    msg = String.valueOf(msgTemp);
                }
            }
        }
        return msg;
    }
}
