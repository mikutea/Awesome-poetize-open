local cjson = require "cjson"
local http = require "resty.http"

-- 安全检查：验证请求URI是否包含可疑字符
local uri = ngx.var.uri
if uri and (
    string.match(uri, "%.%.") or     -- 目录遍历
    string.match(uri, "<") or        -- HTML注入
    string.match(uri, ">") or        -- HTML注入
    string.match(uri, "'") or        -- SQL注入
    string.match(uri, '"') or        -- SQL注入  
    string.match(uri, ";") or        -- 命令注入
    string.match(uri, "&") or        -- 命令注入
    string.match(uri, "|") or        -- 命令注入
    string.len(uri) > 200            -- 超长URI
) then
    ngx.log(ngx.WARN, "IM页面检测到可疑URI，阻止处理: " .. uri)
    return
end

-- 使用ngx.ctx来存储数据
ngx.ctx.seo_data = ""
ngx.ctx.title = ""
ngx.ctx.has_title = false
ngx.ctx.icon_data = ""
-- 添加注入标志，防止重复插入
ngx.ctx.seo_injected = false
ngx.ctx.title_injected = false
ngx.ctx.icon_injected = false

-- 使用HTTP客户端的API调用函数，支持HTTP/2
local function fetch_api(api_path, query_params)
    -- 尝试从缓存读取数据
    local seo_cache = ngx.shared.seo_cache
    local cache_key = api_path
    
    -- 如果有查询参数，将其添加到缓存键中
    if query_params then
        for k, v in pairs(query_params) do
            if type(v) == "string" and v ~= "" then
                cache_key = cache_key .. ":" .. k .. "=" .. v
            end
        end
    end
    
    -- 检查缓存中是否有数据
    local cached_data = seo_cache:get(cache_key)
    if cached_data then
        -- ngx.log(ngx.INFO, "从缓存读取SEO数据: " .. cache_key)
        return cached_data
    end
    
    -- ngx.log(ngx.INFO, "发起HTTP请求: " .. api_path)
    
    -- 创建HTTP客户端
    local httpc = http.new()
    httpc:set_timeout(5000) -- 5秒超时
    
    -- 构建请求URL
    local backend_url = "http://poetize-python:5000" .. api_path
    
    -- 构建查询参数
    local query_string = ""
    if query_params then
        local params = {}
        for k, v in pairs(query_params) do
            if type(v) == "string" and v ~= "" then
                table.insert(params, k .. "=" .. ngx.escape_uri(v))
            end
        end
        if #params > 0 then
            query_string = "?" .. table.concat(params, "&")
        end
    end
    
    -- 发起HTTP请求
    local res, err = httpc:request_uri(backend_url .. query_string, {
        method = "GET",
        headers = {
            ["Host"] = ngx.var.host,
            ["X-Real-IP"] = ngx.var.remote_addr,
            ["X-Forwarded-For"] = ngx.var.proxy_add_x_forwarded_for or ngx.var.remote_addr,
            ["X-Forwarded-Proto"] = ngx.var.scheme,
            ["X-Internal-Service"] = "poetize-nginx",
            ["User-Agent"] = "nginx-lua-seo-client/1.0.0"
        }
    })
    
    if not res then
        ngx.log(ngx.ERR, "HTTP请求失败: " .. (err or "未知错误"))
        return nil
    end
    
    -- ngx.log(ngx.INFO, "HTTP响应状态码: " .. res.status)
    if res.body then
        -- ngx.log(ngx.INFO, "HTTP响应前100个字符: " .. string.sub(res.body, 1, 100))
    end
    
    if res.status ~= 200 then
        ngx.log(ngx.ERR, "HTTP请求状态码异常: " .. res.status)
        return nil
    end
    
    if not res.body or res.body == "" then
        ngx.log(ngx.ERR, "HTTP返回内容为空")
        return nil
    end
    
    -- 尝试解析JSON
    local ok, data = pcall(cjson.decode, res.body)
    if not ok then
        ngx.log(ngx.ERR, "HTTP返回内容不是有效的JSON: " .. res.body)
        -- 尝试直接使用返回的内容作为字符串
        if type(res.body) == "string" then
            return res.body
        end
        return nil
    end
    
    -- 记录解析后的JSON数据结构
    -- ngx.log(ngx.INFO, "解析后的JSON数据类型: " .. type(data))
    if type(data) == "table" then
        if data.status then
            -- ngx.log(ngx.INFO, "JSON status字段: " .. data.status)
        end
        if data.data then
            -- ngx.log(ngx.INFO, "JSON data字段类型: " .. type(data.data))
            if type(data.data) == "string" then
                -- ngx.log(ngx.INFO, "JSON data字段前50个字符: " .. string.sub(data.data, 1, 50))
            end
        else
            ngx.log(ngx.WARN, "JSON中无data字段")
        end
    end
    
    -- 处理JSON格式的返回数据
    if not data then
        ngx.log(ngx.ERR, "解析的JSON数据为空")
        return nil
    end
    
    -- 检查JSON数据中的状态
    if data.status and data.status ~= "success" then
        ngx.log(ngx.ERR, "HTTP返回状态不成功: " .. data.status .. (data.message or ""))
        return nil
    end
    
    -- 检查data字段
    if not data.data then
        ngx.log(ngx.ERR, "HTTP返回中缺少data字段")
        -- 尝试直接返回JSON对象
        return cjson.encode(data)
    end
    
    -- 根据data的类型返回适当的值，并处理title标签
    local result
    if type(data.data) == "string" then
        -- 提取title标签中的内容
        local title_start = string.find(data.data, "<title>")
        local title_end = string.find(data.data, "</title>")
        
        if title_start and title_end and title_end > title_start then
            -- 设置title变量，但从返回数据中移除title标签
            ngx.ctx.title = string.sub(data.data, title_start + 7, title_end - 1)
            -- ngx.log(ngx.INFO, "已提取title并从SEO数据中移除: " .. ngx.ctx.title)
            
            -- 移除title标签
            local before_title = string.sub(data.data, 1, title_start - 1) or ""
            local after_title = string.sub(data.data, title_end + 8) or ""
            result = before_title .. after_title
        else
            result = data.data
        end
    elseif type(data.data) == "table" then
        -- 将JSON表转为字符串
        result = cjson.encode(data.data)
    else
        -- 转换为字符串
        result = tostring(data.data)
    end
    
    -- 缓存结果 (保存12小时)
    if result and type(result) == "string" then
        local success, err = seo_cache:set(cache_key, result, 43200)
        if not success then
            ngx.log(ngx.ERR, "缓存SEO数据失败: " .. (err or "未知错误"))
        else
            -- ngx.log(ngx.INFO, "成功缓存SEO数据: " .. cache_key)
        end
    end
    
    return result
end

-- 生成图标meta标签的函数
local function generate_icon_meta_tags(seo_config)
    if not seo_config or type(seo_config) ~= "table" then
        return ""
    end
    
    local meta_tags = {}
    
    -- 网站标签页图标
    if seo_config.site_icon and seo_config.site_icon ~= "" then
        table.insert(meta_tags, '<link rel="icon" type="image/png" sizes="32x32" href="' .. seo_config.site_icon .. '">')
        table.insert(meta_tags, '<link rel="icon" type="image/png" sizes="16x16" href="' .. seo_config.site_icon .. '">')
        table.insert(meta_tags, '<link rel="shortcut icon" href="' .. seo_config.site_icon .. '">')
    end
    
    -- Apple Touch图标
    if seo_config.apple_touch_icon and seo_config.apple_touch_icon ~= "" then
        table.insert(meta_tags, '<link rel="apple-touch-icon" sizes="180x180" href="' .. seo_config.apple_touch_icon .. '">')
    end
    
    -- PWA图标
    if seo_config.site_icon_192 and seo_config.site_icon_192 ~= "" then
        table.insert(meta_tags, '<link rel="icon" type="image/png" sizes="192x192" href="' .. seo_config.site_icon_192 .. '">')
    end
    
    if seo_config.site_icon_512 and seo_config.site_icon_512 ~= "" then
        table.insert(meta_tags, '<link rel="icon" type="image/png" sizes="512x512" href="' .. seo_config.site_icon_512 .. '">')
    end
    
    -- Manifest文件
    if seo_config.site_icon_192 or seo_config.site_icon_512 then
        table.insert(meta_tags, '<link rel="manifest" href="/manifest.json">')
    end
    
    return table.concat(meta_tags, '\n')
end

-- 为IM页面获取特定的SEO数据
-- ngx.log(ngx.INFO, "尝试获取IM页面特定SEO数据")
local data = fetch_api("/seo/getIMSiteMeta", {})

-- 如果IM特定API不可用，回退到通用站点数据
if not data then
    ngx.log(ngx.WARN, "IM特定SEO数据获取失败，尝试使用通用站点数据")
    data = fetch_api("/seo/getSiteMeta", {})
else
    -- ngx.log(ngx.INFO, "成功获取IM特定SEO数据")
end

if data then
    ngx.ctx.seo_data = data
    -- ngx.log(ngx.INFO, "成功获取SEO数据")
    
    -- 确保seo_data是字符串类型
    if ngx.ctx.seo_data and type(ngx.ctx.seo_data) ~= "string" then
        ngx.log(ngx.WARN, "IM-SEO数据不是字符串，尝试转换")
        local ok, str_data = pcall(function() return tostring(ngx.ctx.seo_data) end)
        if ok and str_data then
            ngx.ctx.seo_data = str_data
        else
            ngx.log(ngx.ERR, "IM-SEO数据转换为字符串失败")
            ngx.ctx.seo_data = ""
        end
    end
    
    -- 提取标题 - 假设seo_data是HTML格式
    local title_start = string.find(ngx.ctx.seo_data, "<title>")
    local title_end = string.find(ngx.ctx.seo_data, "</title>")
    
    if title_start and title_end and title_end > title_start then
        ngx.ctx.title = string.sub(ngx.ctx.seo_data, title_start + 7, title_end - 1)
        -- ngx.log(ngx.INFO, "IM-提取到标题: ", ngx.ctx.title)
        
        -- 移除title标签
        local before_title = string.sub(ngx.ctx.seo_data, 1, title_start - 1) or ""
        local after_title = string.sub(ngx.ctx.seo_data, title_end + 8) or ""
        ngx.ctx.seo_data = before_title .. after_title
    else
        ngx.log(ngx.WARN, "IM-SEO数据中未找到标题标签")
    end
else
    ngx.log(ngx.WARN, "未能获取任何SEO数据")
    -- 使用默认的SEO元数据，而非空字符串
    ngx.ctx.seo_data = '<meta name="description" content="Poetize IM聊天室 - 实时交流平台">'
        .. '<meta name="keywords" content="poetize,im,chat,聊天室,即时通讯">'
        .. '<meta property="og:title" content="Poetize IM聊天室">'
        .. '<meta property="og:description" content="一个优雅的实时聊天平台">'
        .. '<meta property="og:type" content="website">'
    -- ngx.log(ngx.INFO, "使用IM默认硬编码的SEO数据")
end

-- 确保有默认值 - IM专用标题
if not ngx.ctx.title or ngx.ctx.title == "" then
    ngx.ctx.title = "POETIZE IM聊天室"
    -- ngx.log(ngx.INFO, "使用默认IM标题: POETIZE IM聊天室")
end

-- 获取SEO配置用于生成图标 - 使用优化缓存
local seo_cache = ngx.shared.seo_cache
local seo_config_cache_key = "seo_config_global"

-- 先尝试从缓存获取SEO配置
local cached_seo_config = seo_cache:get(seo_config_cache_key)
local seo_config_data

if cached_seo_config then
    seo_config_data = cached_seo_config
    ngx.log(ngx.INFO, "IM-使用缓存的SEO配置数据 (避免HTTP请求)")
else
    ngx.log(ngx.INFO, "IM-SEO配置缓存未命中，发起HTTP请求")
    -- 发起HTTP请求获取SEO配置
    local httpc = http.new()
    httpc:set_timeout(3000) -- 3秒超时
    
    local res, err = httpc:request_uri("http://poetize-python:5000/python/seo/getSeoConfig", {
        method = "GET",
        headers = {
            ["Host"] = ngx.var.host,
            ["X-Real-IP"] = ngx.var.remote_addr,
            ["X-Forwarded-For"] = ngx.var.proxy_add_x_forwarded_for or ngx.var.remote_addr,
            ["X-Forwarded-Proto"] = ngx.var.scheme,
            ["X-Internal-Service"] = "poetize-nginx-im-seo",
            ["User-Agent"] = "nginx-lua-seo-client/1.0.0"
        }
    })
    
    if res and res.status == 200 and res.body then
        local ok, parsed_response = pcall(cjson.decode, res.body)
        if ok and parsed_response and parsed_response.code == 200 and parsed_response.data then
            seo_config_data = cjson.encode(parsed_response.data)
            -- 缓存SEO配置2小时（与主站保持一致）
            local cache_success = seo_cache:set(seo_config_cache_key, seo_config_data, 86400)
            if cache_success then
                ngx.log(ngx.INFO, "IM-SEO配置已缓存24小时")
            else
                ngx.log(ngx.ERR, "IM-SEO配置缓存失败")
            end
        else
            ngx.log(ngx.ERR, "IM-SEO配置响应解析失败")
            seo_config_data = nil
        end
    else
        ngx.log(ngx.ERR, "IM-获取SEO配置HTTP请求失败: " .. (err or "网络错误"))
        seo_config_data = nil
    end
end

-- 处理SEO配置数据
if seo_config_data then
    local ok, seo_config = pcall(cjson.decode, seo_config_data)
    if ok and seo_config and type(seo_config) == "table" then
        ngx.ctx.icon_data = generate_icon_meta_tags(seo_config)
        ngx.log(ngx.DEBUG, "IM-成功生成图标meta标签")
    else
        ngx.log(ngx.WARN, "IM-SEO配置JSON解析失败")
        ngx.ctx.icon_data = ""
    end
else
    ngx.log(ngx.WARN, "IM-无SEO配置数据，使用空图标")
    ngx.ctx.icon_data = ""
end