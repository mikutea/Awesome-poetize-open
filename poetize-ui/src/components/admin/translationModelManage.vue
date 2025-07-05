<template>
  <div class="translation-management">
      
    <!-- 主要配置区域 -->
    <div class="config-container">
      <el-form :model="apiConfig" label-width="120px" class="config-form">

        <!-- 翻译模式选择 -->
        <div class="config-section">
          <!-- 页面标题 -->
          <div style="margin-bottom: 30px;">
            <el-tag effect="dark" class="my-tag">
              <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
                <path
                  d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
                  fill="#8C7BFD"></path>
                <path
                  d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
                  fill="#FFE37B"></path>
              </svg>
              翻译模式配置
            </el-tag>
          </div>
          <div class="section-content">
        <el-form-item label="翻译模式">
          <el-select v-model="apiConfig.mode" placeholder="请选择翻译模式" style="width: 120px" class="mrb10">
            <el-option key="api" label="API翻译" :value="'api'">
              <span class="option-content">
                <i class="el-icon-connection"></i>
                API翻译
              </span>
            </el-option>
            <el-option key="llm" label="大模型翻译" :value="'llm'">
              <span class="option-content">
                <i class="el-icon-chat-round"></i>
                大模型翻译
              </span>
            </el-option>
          </el-select>
        </el-form-item>
          </div>
        </div>

        <!-- 默认语言配置 -->
        <div class="config-section">
              <!-- 页面标题 -->
        <div style="margin-bottom: 30px;">
          <el-tag effect="dark" class="my-tag">
            <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
              <path
                d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
                fill="#8C7BFD"></path>
              <path
                d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
                fill="#FFE37B"></path>
            </svg>
            翻译配置
          </el-tag>
        </div>
          <div class="section-content">
            <div class="language-config-row">
              <el-form-item label="默认源语言" class="language-item">
                <el-select v-model="apiConfig.defaultSourceLang" placeholder="请选择默认源语言" class="language-select">
                  <el-option label="自动检测" value="auto">
                    <span class="option-content">
                      自动检测
                    </span>
                  </el-option>
                  <el-option label="中文" value="zh">
                    <span class="option-content">
                      中文
                    </span>
                  </el-option>
                  <el-option label="英文" value="en">
                    <span class="option-content">
                      英文
                    </span>
                  </el-option>
                  <el-option label="日文" value="ja">
                    <span class="option-content">
                      日文
                    </span>
                  </el-option>
                  <el-option label="韩文" value="ko">
                    <span class="option-content">
                      韩文
                    </span>
                  </el-option>
                  <el-option label="法文" value="fr">
                    <span class="option-content">
                      法文
                    </span>
                  </el-option>
                  <el-option label="德文" value="de">
                    <span class="option-content">
                      德文
                    </span>
                  </el-option>
                  <el-option label="西班牙文" value="es">
                    <span class="option-content">
                      西班牙文
                    </span>
                  </el-option>
                  <el-option label="俄文" value="ru">
                    <span class="option-content">
                      俄文
                    </span>
                  </el-option>
                </el-select>

              </el-form-item>
              
              <div class="language-arrow">
                <i class="el-icon-right"></i>
              </div>
              
              <el-form-item label="默认目标语言" class="language-item">
                <el-select v-model="apiConfig.defaultTargetLang" placeholder="请选择默认目标语言" class="language-select">
                  <el-option label="中文" value="zh">
                    <span class="option-content">
                      中文
                    </span>
                  </el-option>
                  <el-option label="英文" value="en">
                    <span class="option-content">
                      英文
                    </span>
                  </el-option>
                  <el-option label="日文" value="ja">
                    <span class="option-content">
                      日文
                    </span>
                  </el-option>
                  <el-option label="韩文" value="ko">
                    <span class="option-content">
                      韩文
                    </span>
                  </el-option>
                  <el-option label="法文" value="fr">
                    <span class="option-content">
                      法文
                    </span>
                  </el-option>
                  <el-option label="德文" value="de">
                    <span class="option-content">
                      德文
                    </span>
                  </el-option>
                  <el-option label="西班牙文" value="es">
                    <span class="option-content">
                      西班牙文
                    </span>
                  </el-option>
                  <el-option label="俄文" value="ru">
                    <span class="option-content">
                      俄文
                    </span>
                  </el-option>
                </el-select>

              </el-form-item>
            </div>
          </div>
        </div>

        <!-- API翻译配置 -->
        <div class="config-section" v-if="apiConfig.mode === 'api'">
          <div class="section-content">
          <el-form-item label="翻译引擎">
              <el-select v-model="apiConfig.provider" placeholder="请选择翻译引擎" class="full-width">
                <el-option label="百度翻译" value="baidu">
                  <span class="option-content">
                    <i class="el-icon-s-platform"></i>
                    百度翻译
                  </span>
                </el-option>
                <el-option label="自定义API" value="custom">
                  <span class="option-content">
                    <i class="el-icon-s-custom"></i>
                    自定义API
                  </span>
                </el-option>
            </el-select>
          </el-form-item>
          
          <template v-if="apiConfig.provider === 'baidu'">
            <el-form-item label="APP ID">
                <el-input v-model="apiConfig.appId" placeholder="请输入百度翻译APP ID" class="input-field"></el-input>
            </el-form-item>
            <el-form-item label="密钥">
                <el-input v-model="apiConfig.appSecret" type="password" show-password placeholder="请输入百度翻译密钥" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.hasExistingBaiduSecret">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                API密钥将自动加密存储，确保您的数据安全
                  </template>
              </div>
            </el-form-item>
          </template>
          
          <template v-if="apiConfig.provider === 'custom'">
            <el-form-item label="API地址">
                <el-input v-model="apiConfig.customUrl" placeholder="请输入自定义翻译API地址" class="input-field"></el-input>
            </el-form-item>
            <el-form-item label="API密钥">
                <el-input v-model="apiConfig.customApiKey" type="password" show-password placeholder="请输入自定义API密钥" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.hasExistingCustomSecret">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                API密钥将自动加密存储，确保您的数据安全
                  </template>
              </div>
            </el-form-item>
            <el-form-item label="密钥2(可选)">
                <el-input v-model="apiConfig.appSecret" type="password" show-password placeholder="某些API需要第二个密钥参数" class="input-field">
                <template slot="prefix">
                  <i class="el-icon-lock"></i>
                </template>
              </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  <template v-if="apiConfig.hasExistingBaiduSecret">
                    已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥
                  </template>
                  <template v-else>
                API密钥将自动加密存储，确保您的数据安全
                  </template>
              </div>
            </el-form-item>
              <div class="info-panel">
                <div class="info-header">
                  <i class="el-icon-question"></i>
                  自定义API使用说明
                </div>
                <div class="info-content">
                  <div class="info-item">• API地址: 翻译服务的完整URL</div>
                  <div class="info-item">• API密钥: 用于身份验证的密钥</div>
                  <div class="info-item">• 密钥2: 某些API（如百度）需要第二个参数</div>
                  <div class="info-item">• 请确保API支持POST请求和JSON格式数据</div>
                </div>
              </div>
          </template>
          </div>
        </div>
        
        <!-- 大模型翻译配置 -->
        <div class="config-section" v-if="apiConfig.mode === 'llm'">
          <div class="section-content">
          <el-form-item label="大模型类型">
              <el-select v-model="apiConfig.llmType" placeholder="请选择大模型类型" class="full-width">
                <el-option label="OpenAI (GPT)" value="openai">
                  <span class="option-content">
                    OpenAI (GPT)
                  </span>
                </el-option>
                <el-option label="Anthropic (Claude)" value="anthropic">
                  <span class="option-content">
                    Anthropic (Claude)
                  </span>
                </el-option>
                <el-option label="Azure OpenAI" value="azure">
                  <span class="option-content">
                    Azure OpenAI
                  </span>
                </el-option>
                <el-option label="自定义/其他" value="custom">
                  <span class="option-content">
                    自定义/其他
                  </span>
                </el-option>
            </el-select>

          </el-form-item>
            
          <el-form-item label="模型名称">
            <el-select 
              v-model="apiConfig.llmModel" 
              :placeholder="getModelPlaceholder()" 
              filterable 
              allow-create
                :class="{'full-width': true, 'custom-model-select': apiConfig.llmType === 'custom'}">
              <el-option 
                v-for="model in availableModels" 
                :key="model.value" 
                :label="model.label" 
                :value="model.value">
              </el-option>
            </el-select>
              <div :class="{'form-tip': true, 'custom-model-tip': apiConfig.llmType === 'custom'}">
                <i class="el-icon-lightbulb"></i>
              {{ getModelTip() }}
            </div>
          </el-form-item>
            
            <!-- 自定义模型的接口类型选择 -->
            <el-form-item label="接口类型" v-if="apiConfig.llmType === 'custom'">
              <el-select v-model="apiConfig.llmInterfaceType" placeholder="请选择接口类型" class="full-width">
                <el-option label="自动检测" value="auto">
                  <span class="option-content">
                    自动检测
                  </span>
                </el-option>
                <el-option label="OpenAI兼容接口" value="openai">
                  <span class="option-content">
                    OpenAI兼容接口
                  </span>
                </el-option>
                <el-option label="Anthropic兼容接口" value="anthropic">
                  <span class="option-content">
                    Anthropic兼容接口
                  </span>
                </el-option>
                <el-option label="自定义HTTP接口" value="custom">
                  <span class="option-content">
                    自定义HTTP接口
                  </span>
                </el-option>
              </el-select>

          </el-form-item>
            
            <el-form-item label="API接口地址">
              <el-input v-model="apiConfig.llmUrl" placeholder="请输入大模型API接口地址" class="input-field"></el-input>

            </el-form-item>
            
            <el-form-item label="API密钥" v-if="needsApiKey">
              <el-input v-model="apiConfig.llmApiKey" type="password" show-password placeholder="请输入API密钥" class="input-field">
              <template slot="prefix">
                <i class="el-icon-lock"></i>
              </template>
            </el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                <template v-if="apiConfig.hasExistingLlmKey">
                  已有密钥已加密保存，留空则保持不变，输入新密钥将覆盖原密钥。对于本地模型（如Ollama）可以不填写
                </template>
                <template v-else>
              API密钥将自动加密存储，确保您的数据安全。对于本地模型（如Ollama）可以不填写
                </template>
            </div>
          </el-form-item>
            
          <el-form-item label="翻译提示词">
              <el-input type="textarea" v-model="apiConfig.llmPrompt" :rows="3" placeholder="请输入翻译提示词，用于指导大模型如何进行翻译" class="textarea-field"></el-input>
              <div class="form-tip">
                <i class="el-icon-info"></i>
                可使用占位符：{source_lang}和{target_lang}表示语言，{format}表示文本格式(Markdown或纯文本)
              </div>
          </el-form-item>
            
          <el-form-item label="超时时间">
              <div class="timeout-group">
                <el-input v-model.number="apiConfig.llmTimeout" placeholder="请输入超时时间" class="timeout-input">
                  <template slot="append">秒</template>
                </el-input>
              </div>

          </el-form-item>
          </div>
        </div>
        
        <!-- 智能摘要配置 -->
        <div class="config-section" v-if="apiConfig.mode === 'llm'">
              <!-- 页面标题 -->
        <div style="margin-bottom: 30px;">
          <el-tag effect="dark" class="my-tag">
            <svg viewBox="0 0 1024 1024" width="20" height="20" style="vertical-align: -4px;">
              <path
                d="M767.1296 808.6528c16.8448 0 32.9728 2.816 48.0256 8.0384 20.6848 7.1168 43.52 1.0752 57.1904-15.9744a459.91936 459.91936 0 0 0 70.5024-122.88c7.8336-20.48 1.0752-43.264-15.9744-57.088-49.6128-40.192-65.0752-125.3888-31.3856-185.856a146.8928 146.8928 0 0 1 30.3104-37.9904c16.2304-14.5408 22.1696-37.376 13.9264-57.6a461.27104 461.27104 0 0 0-67.5328-114.9952c-13.6192-16.9984-36.4544-22.9376-57.0368-15.8208a146.3296 146.3296 0 0 1-48.0256 8.0384c-70.144 0-132.352-50.8928-145.2032-118.7328-4.096-21.6064-20.736-38.5536-42.4448-41.8304-22.0672-3.2768-44.6464-5.0176-67.6864-5.0176-21.4528 0-42.5472 1.536-63.232 4.4032-22.3232 3.1232-40.2432 20.48-43.52 42.752-6.912 46.6944-36.0448 118.016-145.7152 118.4256-17.3056 0.0512-33.8944-2.9696-49.3056-8.448-21.0432-7.4752-44.3904-1.4848-58.368 15.9232A462.14656 462.14656 0 0 0 80.4864 348.16c-7.6288 20.0192-2.7648 43.008 13.4656 56.9344 55.5008 47.8208 71.7824 122.88 37.0688 185.1392a146.72896 146.72896 0 0 1-31.6416 39.168c-16.8448 14.7456-23.0912 38.1952-14.5408 58.9312 16.896 41.0112 39.5776 79.0016 66.9696 113.0496 13.9264 17.3056 37.2736 23.1936 58.2144 15.7184 15.4112-5.4784 32-8.4992 49.3056-8.4992 71.2704 0 124.7744 49.408 142.1312 121.2928 4.9664 20.48 21.4016 36.0448 42.24 39.168 22.2208 3.328 44.9536 5.0688 68.096 5.0688 23.3984 0 46.4384-1.792 68.864-5.1712 21.3504-3.2256 38.144-19.456 42.7008-40.5504 14.8992-68.8128 73.1648-119.7568 143.7696-119.7568z"
                fill="#8C7BFD"></path>
              <path
                d="M511.8464 696.3712c-101.3248 0-183.7568-82.432-183.7568-183.7568s82.432-183.7568 183.7568-183.7568 183.7568 82.432 183.7568 183.7568-82.432 183.7568-183.7568 183.7568z m0-265.1648c-44.8512 0-81.3568 36.5056-81.3568 81.3568S466.9952 593.92 511.8464 593.92s81.3568-36.5056 81.3568-81.3568-36.5056-81.3568-81.3568-81.3568z"
                fill="#FFE37B"></path>
            </svg>
            智能摘要配置
          </el-tag>
        </div>
          <div class="section-content">
            <el-form-item label="启用AI摘要">
              <el-switch 
                v-model="apiConfig.summaryAiEnabled" 
                active-text="启用" 
                inactive-text="禁用"
                @change="onSummaryToggle">
              </el-switch>

            </el-form-item>
            
            <template v-if="apiConfig.summaryAiEnabled">
              <el-form-item label="摘要风格">
                <el-select v-model="apiConfig.summaryStyle" placeholder="请选择摘要风格" class="full-width">
                  <el-option label="简洁明了" value="concise">
                    <span class="option-content">
                      <i class="el-icon-document-copy"></i>
                      简洁明了
                    </span>
                  </el-option>
                  <el-option label="详细描述" value="detailed">
                    <span class="option-content">
                      <i class="el-icon-reading"></i>
                      详细描述
                    </span>
                  </el-option>
                  <el-option label="学术风格" value="academic">
                    <span class="option-content">
                      <i class="el-icon-notebook-2"></i>
                      学术风格
                    </span>
                  </el-option>
                </el-select>

              </el-form-item>
              
              <el-form-item label="摘要长度">
                <el-input-number 
                  v-model="apiConfig.summaryMaxLength" 
                  :min="50" 
                  :max="500" 
                  :step="10"
                  placeholder="请输入摘要最大长度"
                  class="number-input">
                </el-input-number>

              </el-form-item>
              
              <el-form-item label="摘要提示词">
                <el-input 
                  type="textarea" 
                  v-model="apiConfig.summaryPrompt" 
                  :rows="3" 
                  placeholder="请输入摘要生成的提示词，用于指导AI如何生成摘要"
                  class="textarea-field">
                </el-input>
                <div class="form-tip">
                  <i class="el-icon-info"></i>
                  可使用占位符：{style}摘要风格，{max_length}最大长度，{format}文本格式。留空则使用默认提示词
                </div>
              </el-form-item>
              
              <el-form-item label="测试摘要">
                <el-button type="info" @click="testSummary" class="action-btn" :loading="testSummaryLoading">
                  测试摘要生成
                </el-button>

              </el-form-item>
            </template>
          </div>
        </div>
        
        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button type="primary" @click="saveApiConfig" class="action-btn primary-btn">
            <i class="el-icon-check"></i>
            保存配置
          </el-button>
          <el-button @click="getApiConfig" class="action-btn">
            <i class="el-icon-refresh"></i>
            刷新配置
          </el-button>
          <el-button type="success" @click="testTranslation" class="action-btn success-btn">
            <i class="el-icon-s-promotion"></i>
            测试翻译
          </el-button>
        </div>
      </el-form>
    </div>
    
    <!-- 测试翻译对话框 -->
    <el-dialog title="测试翻译" :visible.sync="testTranslationDialogVisible" width="60%" class="test-dialog">
      <div class="dialog-content">
        <div class="test-form">
          <div class="language-info">
            <div class="lang-display">
              <span class="lang-text">{{ getLanguageName(apiConfig.defaultSourceLang) }}</span>
              <div class="arrow-separator">
                <i class="el-icon-right"></i>
              </div>
              <span class="lang-text">{{ getLanguageName(apiConfig.defaultTargetLang) }}</span>
            </div>
            <div class="lang-note">
              <i class="el-icon-info"></i>
              使用配置中的默认语言设置
            </div>
          </div>
          
          <div class="input-section">
            <label>源文本</label>
            <el-input type="textarea" v-model="testTranslationForm.sourceText" :rows="4" placeholder="请输入要翻译的文本" class="source-input"></el-input>
          </div>
          
          <div class="stream-mode">
            <label>翻译模式</label>
            <div class="stream-options">
              <el-radio v-model="testTranslationForm.useStream" :label="true" class="stream-radio">流式翻译（实时显示）</el-radio>
              <el-radio v-model="testTranslationForm.useStream" :label="false" class="stream-radio">普通翻译（完成后显示）</el-radio>
            </div>
          </div>
          
          <div class="translate-section">
            <el-button type="primary" @click="doTestTranslation" :loading="testTranslationLoading" class="translate-btn">
              <i class="el-icon-s-promotion"></i>
              {{ testTranslationLoading ? '翻译中...' : '开始翻译' }}
            </el-button>
          </div>
          
          <div class="result-section" v-if="testTranslationForm.translatedText">
            <label>翻译结果</label>
            <el-input type="textarea" v-model="testTranslationForm.translatedText" :rows="4" readonly class="result-output"></el-input>
            <div class="result-meta" v-if="testTranslationForm.processingTime">
              <el-tag size="small" type="info">
                <i class="el-icon-time"></i>
                用时: {{ testTranslationForm.processingTime.toFixed(2) }}秒
              </el-tag>
              <el-tag size="small" type="success" v-if="testTranslationForm.detectedLang">
                <i class="el-icon-search"></i>
                检测语言: {{ testTranslationForm.detectedLang === 'zh' ? '中文' : '英文' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testTranslationDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- 摘要测试对话框 -->
    <el-dialog title="测试AI摘要" :visible.sync="testSummaryDialogVisible" width="70%" class="test-dialog">
      <div class="dialog-content">
        <div class="test-form">
          <div class="summary-info">
            <div class="summary-display">
              <el-tag type="info" size="medium">
                {{ apiConfig.summaryStyle === 'concise' ? '简洁明了' : 
                   apiConfig.summaryStyle === 'detailed' ? '详细描述' : '学术风格' }}
              </el-tag>
              <el-tag type="success" size="medium">
                <i class="el-icon-document"></i>
                最大长度: {{ apiConfig.summaryMaxLength }}字符
              </el-tag>
            </div>
            <div class="summary-note">
              <i class="el-icon-info"></i>
              使用当前AI摘要配置生成测试摘要
            </div>
          </div>
          
          <div class="input-section">
            <label>测试内容</label>
            <el-input 
              type="textarea" 
              v-model="testSummaryForm.content" 
              :rows="8" 
              placeholder="请输入要生成摘要的文章内容，支持Markdown格式"
              class="source-input">
            </el-input>
            <div class="input-tips">
              <el-tag size="mini" type="warning">提示</el-tag>
              <span>建议输入至少500字符的内容以获得更好的摘要效果</span>
            </div>
          </div>
          
          <div class="test-section">
            <el-button 
              type="primary" 
              @click="doTestSummary" 
              :loading="testSummaryLoading" 
              class="test-btn">
              <i class="el-icon-magic-stick"></i>
              {{ testSummaryLoading ? '生成中...' : '生成摘要' }}
            </el-button>
          </div>
          
          <div class="result-section" v-if="testSummaryForm.summary">
            <label>生成的摘要</label>
            <el-input 
              type="textarea" 
              v-model="testSummaryForm.summary" 
              :rows="4" 
              readonly 
              class="result-output">
            </el-input>
            <div class="result-meta" v-if="testSummaryForm.processingTime">
              <el-tag size="small" type="info">
                <i class="el-icon-time"></i>
                用时: {{ testSummaryForm.processingTime }}秒
              </el-tag>
              <el-tag size="small" type="success" v-if="testSummaryForm.method">
                <i class="el-icon-cpu"></i>
                方法: {{ 
                  testSummaryForm.method === 'ai-openai' ? 'OpenAI' :
                  testSummaryForm.method === 'ai-anthropic' ? 'Claude' :
                  testSummaryForm.method === 'ai-custom' ? '自定义AI' :
                  testSummaryForm.method === 'textrank' ? 'TextRank' : 
                  testSummaryForm.method 
                }}
              </el-tag>
              <el-tag size="small" type="warning">
                <i class="el-icon-document-copy"></i>
                长度: {{ testSummaryForm.summary.length }}字符
              </el-tag>
            </div>
          </div>
          
          <div class="error-section" v-if="testSummaryForm.error">
            <label>错误信息</label>
            <el-alert
              :title="testSummaryForm.error"
              type="error"
              show-icon>
            </el-alert>
          </div>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="testSummaryDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="resetSummaryTest">重新测试</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ArticleAiAssistant',
  data() {
    return {
      apiConfig: {
        mode: 'llm',
        provider: 'baidu',
        appId: '',
        appSecret: '',
        customUrl: '',
        customApiKey: '',
        llmType: 'openai',
        llmModel: 'gpt-3.5-turbo',
        llmUrl: '',
        llmApiKey: '',
        llmPrompt: '',
        llmTimeout: 30,
        llmInterfaceType: 'auto',
        // 默认语言配置
        defaultSourceLang: 'zh',
        defaultTargetLang: 'en',
        // 密钥状态标记
        hasExistingBaiduSecret: false,
        hasExistingCustomKey: false,
        hasExistingCustomSecret: false,  // 添加自定义API第二密钥状态标记
        hasExistingLlmKey: false,
        summaryAiEnabled: true,
        summaryStyle: 'concise',
        summaryMaxLength: 100,
        summaryPrompt: '请为以下文章生成一个简洁明了的摘要，突出文章的核心观点和主要内容，摘要长度控制在{max_length}字符以内。请直接返回摘要内容，不要添加任何前缀或说明：'
      },
      testTranslationDialogVisible: false,
      testTranslationForm: {
        sourceText: '',
        translatedText: '',
        processingTime: null,
        detectedLang: null,
        useStream: false  // Python端暂不支持流式翻译
      },
      testTranslationLoading: false,
      testSummaryLoading: false,
      testSummaryDialogVisible: false,
      testSummaryForm: {
        content: `# Vue.js入门指南

Vue.js是一个用于构建用户界面的渐进式JavaScript框架。与其它大型框架不同的是，Vue被设计为可以自底向上逐层应用。

## 核心特性

Vue.js的核心库只关注视图层，不仅易于上手，还便于与第三方库或既有项目整合。另一方面，当与现代化的工具链以及各种支持类库结合使用时，Vue也完全能够为复杂的单页应用提供驱动。

## 响应式数据绑定

Vue.js具有响应式数据绑定和组件化的特性，这使得开发者可以轻松构建动态的Web应用程序。通过数据绑定，开发者可以轻松地将数据与视图同步，无需手动操作DOM。`,
        summary: '',
        processingTime: null,
        method: null,
        error: null
      }
    };
  },
  created() {
    this.getApiConfig();
  },
  computed: {
    needsApiKey() {
      // 所有大模型类型都可能需要API密钥
      return true;
    },
    availableModels() {
      const models = [];
      
      switch (this.apiConfig.llmType) {
        case 'openai':
          models.push(
            { label: 'GPT-4.1 (最新)', value: 'gpt-4.1' },
            { label: 'GPT-4o (强大)', value: 'gpt-4o' },
            { label: 'GPT-4o Mini (性价比)', value: 'gpt-4o-mini' },
            { label: 'GPT-4 Turbo (经典)', value: 'gpt-4-turbo' },
            { label: 'GPT-4 (稳定)', value: 'gpt-4' },
            { label: 'GPT-3.5 Turbo (快速)', value: 'gpt-3.5-turbo' },
            { label: 'GPT-3.5 Turbo 16k', value: 'gpt-3.5-turbo-16k' }
          );
          break;
        case 'anthropic':
          models.push(
            { label: 'Claude-4 (最新)', value: 'claude-4' },
            { label: 'Claude-3.7 Sonnet (强大)', value: 'claude-3-7-sonnet' },
            { label: 'Claude-3.5 Sonnet (稳定)', value: 'claude-3-5-sonnet-20241022' },
            { label: 'Claude-3.5 Haiku (快速)', value: 'claude-3-5-haiku-20241022' }
          );
          break;
        case 'azure':
          models.push(
            { label: 'Azure GPT-4.1', value: 'gpt-4.1' },
            { label: 'Azure GPT-4o', value: 'gpt-4o' },
            { label: 'Azure GPT-4o Mini', value: 'gpt-4o-mini' },
            { label: 'Azure GPT-4 Turbo', value: 'gpt-4-turbo' },
            { label: 'Azure GPT-4', value: 'gpt-4' },
            { label: 'Azure GPT-3.5 Turbo', value: 'gpt-35-turbo' },
            { label: 'Azure GPT-3.5 Turbo 16k', value: 'gpt-35-turbo-16k' }
          );
          break;
        case 'custom':
          models.push(
            // Ollama本地模型 - 最新版本
            { label: 'Qwen3:8B (通义千问最新)', value: 'qwen3:8b' },
            { label: 'Qwen3:14B (通义千问)', value: 'qwen3:14b' },
            { label: 'Qwen3:32B (通义千问)', value: 'qwen3:32b' },
            { label: 'Qwen2.5:7B (通义千问)', value: 'qwen2.5:7b' },
            { label: 'Qwen2.5:14B (通义千问)', value: 'qwen2.5:14b' },
            { label: 'Qwen2.5:32B (通义千问)', value: 'qwen2.5:32b' },
            { label: 'Llama3.2:3B (Meta)', value: 'llama3.2:3b' },
            { label: 'Llama3.1:8B (Meta)', value: 'llama3.1:8b' },
            { label: 'Gemma2:9B (Google)', value: 'gemma2:9b' },
            { label: 'Gemma2:27B (Google)', value: 'gemma2:27b' },
            { label: 'DeepSeek-Coder:6.7B', value: 'deepseek-coder:6.7b' },
            { label: 'CodeLlama:7B (Meta)', value: 'codellama:7b' },
            { label: 'Mistral:7B', value: 'mistral:7b' },
            { label: 'Yi:6B (零一万物)', value: 'yi:6b' },
            { label: 'Phi3:3.8B (Microsoft)', value: 'phi3:3.8b' },
            // 云端API模型
            { label: '通义千问 Turbo', value: 'qwen-turbo' },
            { label: '通义千问 Plus', value: 'qwen-plus' },
            { label: '通义千问 Max', value: 'qwen-max' },
            { label: '文心一言 4.0', value: 'ernie-4.0-8k' },
            { label: '文心一言 3.5', value: 'ernie-3.5-8k' },
            { label: '智谱清言 GLM-4', value: 'glm-4' },
            { label: '智谱清言 GLM-3 Turbo', value: 'glm-3-turbo' }
          );
          break;
      }
      
      return models;
    }
  },
  methods: {
    // API配置相关方法
    async getApiConfig() {
      try {
        this.loading = true;
        const res = await this.$http.get(this.$constant.pythonBaseURL + '/api/translation/config');
        
        console.log('获取到的配置数据:', res); // 添加调试日志
        
        if (res && res.code === 200 && res.data) {
          // 设置当前翻译类型
          this.apiConfig.mode = res.data.type === 'llm' ? 'llm' : 'api';
          
          console.log('设置模式为:', this.apiConfig.mode); // 添加调试日志
          
          // 处理百度翻译配置
          if (res.data.baidu) {
            this.apiConfig.provider = 'baidu';
            this.apiConfig.appId = res.data.baidu.app_id || '';
            // 检查是否有已配置的密钥（加密密钥会以特定格式返回）
            this.apiConfig.hasExistingBaiduSecret = !!(res.data.baidu.app_secret && res.data.baidu.app_secret !== '');
            this.apiConfig.appSecret = ''; // 不显示已有密钥内容，用户可选择覆盖
          }
          
          // 处理自定义API配置
          if (res.data.custom) {
            if (res.data.type === 'custom') {
              this.apiConfig.provider = 'custom';
            }
            this.apiConfig.customUrl = res.data.custom.api_url || '';
            this.apiConfig.hasExistingCustomKey = !!(res.data.custom.api_key && res.data.custom.api_key !== '');
            this.apiConfig.customApiKey = ''; // 不显示已有密钥内容
            // 处理自定义API的第二个密钥字段
            this.apiConfig.hasExistingCustomSecret = !!(res.data.custom.app_secret && res.data.custom.app_secret !== '');
            this.apiConfig.appSecret = ''; // 不显示已有密钥内容
          }
          
          // 处理LLM配置
          if (res.data.llm) {
            console.log('LLM配置数据:', res.data.llm); // 添加调试日志
            this.apiConfig.llmModel = res.data.llm.model || '';
            this.apiConfig.llmUrl = res.data.llm.api_url || '';
            this.apiConfig.hasExistingLlmKey = !!(res.data.llm.api_key && res.data.llm.api_key !== '' && res.data.llm.api_key !== 'null');
            this.apiConfig.llmApiKey = ''; // 不显示已有密钥内容
            this.apiConfig.llmPrompt = res.data.llm.prompt || '';
            this.apiConfig.llmInterfaceType = res.data.llm.interface_type || 'auto';  // 读取接口类型
            this.apiConfig.llmTimeout = res.data.llm.timeout || 30;  // 读取超时时间
            
            console.log('LLM配置读取结果:', {
              model: this.apiConfig.llmModel,
              url: this.apiConfig.llmUrl,
              hasKey: this.apiConfig.hasExistingLlmKey,
              prompt: this.apiConfig.llmPrompt,
              interfaceType: this.apiConfig.llmInterfaceType
            }); // 添加调试日志
            
            // 根据模型类型推断LLM类型
            if (res.data.llm.model) {
              const model = res.data.llm.model.toLowerCase();
              if (model.includes('gpt') || model.includes('openai')) {
                this.apiConfig.llmType = 'openai';
              } else if (model.includes('claude') || model.includes('anthropic')) {
                this.apiConfig.llmType = 'anthropic';
              } else if (model.includes('azure')) {
                this.apiConfig.llmType = 'azure';
          } else {
                this.apiConfig.llmType = 'custom';
              }
            }
          }
          
          // 处理默认语言配置
          if (res.data.default_source_lang) {
            this.apiConfig.defaultSourceLang = res.data.default_source_lang;
          }
          if (res.data.default_target_lang) {
            this.apiConfig.defaultTargetLang = res.data.default_target_lang;
          }
          
          // 处理摘要配置
          if (res.data.summary) {
            this.apiConfig.summaryAiEnabled = res.data.summary.ai_enabled !== false; // 默认为true，只有明确设置为false时才为false
            this.apiConfig.summaryStyle = res.data.summary.style || 'concise';
            this.apiConfig.summaryMaxLength = res.data.summary.max_length || 150;
            this.apiConfig.summaryPrompt = res.data.summary.prompt || '请为以下文章生成一个简洁明了的摘要，突出文章的核心观点和主要内容，摘要长度控制在{max_length}字符以内。请直接返回摘要内容，不要添加任何前缀或说明：';
          } else {
            // 如果没有摘要配置，使用默认值
            this.apiConfig.summaryAiEnabled = true;
            this.apiConfig.summaryStyle = 'concise';
            this.apiConfig.summaryMaxLength = 150;
            this.apiConfig.summaryPrompt = '请为以下文章生成一个简洁明了的摘要，突出文章的核心观点和主要内容，摘要长度控制在{max_length}字符以内。请直接返回摘要内容，不要添加任何前缀或说明：';
          }
        } else {
          this.$message.error('获取配置失败：' + (res?.message || '未知错误'));
        }
      } catch (error) {
        console.error('获取API配置失败:', error);
        this.$message.error('获取配置失败，请检查网络连接');
      } finally {
        this.loading = false;
      }
    },
    async saveApiConfig() {
      try {
        this.loading = true;
        
        // 构建配置对象
        const config = {};
        
        // 根据模式设置不同的配置
        if (this.apiConfig.mode === 'api') {
          if (this.apiConfig.provider === 'baidu') {
            config.type = 'baidu';
            config.baidu = {
              app_id: this.apiConfig.appId
            };
            // 只有输入了新密钥才发送
            if (this.apiConfig.appSecret && this.apiConfig.appSecret.trim() !== '') {
              config.baidu.app_secret = this.apiConfig.appSecret;
            }
          } else if (this.apiConfig.provider === 'custom') {
            config.type = 'custom';
            config.custom = {
              api_url: this.apiConfig.customUrl
            };
            // 只有输入了新密钥才发送
            if (this.apiConfig.customApiKey && this.apiConfig.customApiKey.trim() !== '') {
              config.custom.api_key = this.apiConfig.customApiKey;
            }
            if (this.apiConfig.appSecret && this.apiConfig.appSecret.trim() !== '') {
              config.custom.app_secret = this.apiConfig.appSecret;
            }
          }
        } else if (this.apiConfig.mode === 'llm') {
          config.type = 'llm';
          config.llm = {
            model: this.apiConfig.llmModel,
            api_url: this.apiConfig.llmUrl,
            prompt: this.apiConfig.llmPrompt || '请将以下文本翻译成中文：',
            interface_type: this.apiConfig.llmInterfaceType || 'auto',  // 保存接口类型
            timeout: this.apiConfig.llmTimeout || 30  // 保存超时时间
          };
          // 只有输入了新密钥才发送
          if (this.apiConfig.llmApiKey && this.apiConfig.llmApiKey.trim() !== '') {
            config.llm.api_key = this.apiConfig.llmApiKey;
          }
        }
        
        // 添加默认语言配置
        config.default_source_lang = this.apiConfig.defaultSourceLang || 'zh';
        config.default_target_lang = this.apiConfig.defaultTargetLang || 'en';
        
        // 添加摘要配置
        if (this.apiConfig.mode === 'llm') {
          config.summary = {
            ai_enabled: this.apiConfig.summaryAiEnabled !== false, // 默认为true，只有明确设置为false时才为false
            style: this.apiConfig.summaryStyle || 'concise',
            max_length: this.apiConfig.summaryMaxLength || 150,
            prompt: this.apiConfig.summaryPrompt || '请为以下文章生成一个简洁明了的摘要，突出文章的核心观点和主要内容，摘要长度控制在{max_length}字符以内。请直接返回摘要内容，不要添加任何前缀或说明：'
          };
        }
        
        const res = await this.$http.post(this.$constant.pythonBaseURL + '/api/translation/config', config);
        
        if (res && res.code === 200) {
          this.$message.success('配置保存成功');
          // 重新加载配置以更新状态
          await this.getApiConfig();
        } else {
          this.$message.error('保存失败：' + (res?.message || '未知错误'));
        }
      } catch (error) {
        console.error('保存API配置失败:', error);
        this.$message.error('保存失败，请检查网络连接');
      } finally {
        this.loading = false;
      }
    },
    // 测试翻译相关方法
    testTranslation() {
      this.testTranslationForm = {
        sourceText: '',
        translatedText: '',
        processingTime: null,
        detectedLang: null,
        useStream: false  // Python端暂不支持流式翻译
      };
      this.testTranslationDialogVisible = true;
    },
    async doTestTranslation() {
      if (!this.testTranslationForm.sourceText) {
        this.$message.warning('请输入要翻译的文本');
        return;
      }
      
      this.testTranslationLoading = true;
      this.testTranslationForm.translatedText = ''; // 清空之前的结果
      
      try {
          await this.doNormalTranslation();
      } catch (error) {
        console.error('测试翻译失败:', error);
        if (error.name === 'AbortError') {
          this.$message.error('翻译超时，请重试');
        } else {
          this.$message.error('测试翻译失败: ' + (error.message || '未知错误'));
        }
      } finally {
        this.testTranslationLoading = false;
      }
    },
    
    async doNormalTranslation() {
      this.$message.info('正在翻译，请稍候...');
      const startTime = Date.now();
      
      try {
        // 使用Python端的翻译测试接口，后端会自动使用配置中的默认语言
        const response = await this.$http.post(this.$constant.pythonBaseURL + '/api/translation/test', {
          text: this.testTranslationForm.sourceText
        });
        
        if (response.code === 200 && response.data) {
          this.testTranslationForm.translatedText = response.data.translated_text;
          this.testTranslationForm.processingTime = (Date.now() - startTime) / 1000;
          this.$message.success(`翻译成功 (引擎: ${response.data.engine})`);
          
          // 如果后端返回了实际使用的语言信息，可以更新显示
          if (response.data.source_lang && response.data.target_lang) {
            console.log(`翻译语言: ${response.data.source_lang} -> ${response.data.target_lang}`);
          }
        } else {
          this.$message.error(response.message || '翻译失败');
        }
      } catch (error) {
        console.error('测试翻译失败:', error);
        this.$message.error('测试翻译失败: ' + (error.message || '未知错误'));
      }
    },
    formatTime(timeString) {
      if (!timeString) return '';
      const date = new Date(timeString);
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
    },
    getModelPlaceholder() {
      switch (this.apiConfig.llmType) {
        case 'openai':
          return '如 gpt-4.1, gpt-4o, gpt-4-turbo, gpt-3.5-turbo';
        case 'anthropic':
          return '如 claude-4, claude-3-7-sonnet, claude-3-5-sonnet-20241022';
        case 'azure':
          return '如 gpt-4.1, gpt-4o, gpt-4-turbo (Azure部署名称)';
        case 'custom':
          return '请输入自定义模型名称，如 qwen3:8b, qwen2.5:7b, llama3:8b, 或任何其他模型';
        default:
          return '请输入模型名称';
      }
    },
    getModelTip() {
      switch (this.apiConfig.llmType) {
        case 'openai':
          return '选择OpenAI模型：GPT-4.1最新版本功能最强，GPT-4o强大且稳定，GPT-4o Mini性价比高，GPT-3.5 Turbo速度快成本低';
        case 'anthropic':
          return '选择Anthropic Claude模型：Claude-4最新版本功能最强，Claude-3.7 Sonnet性能强大，Claude-3.5系列稳定可靠，Haiku速度快';
        case 'azure':
          return '输入Azure OpenAI部署的模型名称，需要与Azure门户中配置的部署名称完全一致。支持GPT-4.1等最新模型';
        case 'custom':
          return '支持多种模型和接口格式：本地模型（Ollama: qwen3、llama3等）、云端API（通义千问、文心一言等）。请选择对应的接口类型以确保正确通信';
        default:
          return '指定要使用的模型名称，确保与API服务提供商的模型名称一致';
      }
    },
    getLanguageName(langCode) {
      const languages = {
        zh: '中文',
        en: '英文',
        ja: '日文',
        ko: '韩文',
        fr: '法文',
        de: '德文',
        es: '西班牙文',
        ru: '俄文'
      };
      return languages[langCode] || langCode;
    },
    onSummaryToggle() {
      if (this.apiConfig.summaryAiEnabled) {
        this.$message.info('已启用AI智能摘要，文章保存时将优先使用AI生成摘要');
      } else {
        this.$message.info('已禁用AI智能摘要，将使用TextRank算法生成摘要');
      }
    },
    testSummary() {
      if (!this.apiConfig.summaryAiEnabled) {
        this.$message.warning('请先启用AI智能摘要功能');
        return;
      }
      
      // 重置表单数据
      this.testSummaryForm.summary = '';
      this.testSummaryForm.processingTime = null;
      this.testSummaryForm.method = null;
      this.testSummaryForm.error = null;
      
      // 打开对话框
      this.testSummaryDialogVisible = true;
    },
    async doTestSummary() {
      if (!this.testSummaryForm.content.trim()) {
        this.$message.warning('请输入要生成摘要的内容');
        return;
      }
      
      this.testSummaryLoading = true;
      this.testSummaryForm.summary = '';
      this.testSummaryForm.error = null;
      
      try {
        const testRequest = {
          content: this.testSummaryForm.content,
          max_length: this.apiConfig.summaryMaxLength,
          style: this.apiConfig.summaryStyle
        };
        
        // 前端超时时间 = 配置的超时时间 + 10秒缓冲
        const timeoutMs = ((this.apiConfig.llmTimeout || 30) + 10) * 1000;
        console.log(`后端配置超时: ${this.apiConfig.llmTimeout}秒，前端超时: ${(this.apiConfig.llmTimeout || 30) + 10}秒`);
        
        const res = await this.$http.post(this.$constant.pythonBaseURL + '/api/translation/test-summary', testRequest, {
          timeout: timeoutMs
        });
        
        if (res && res.code === 200 && res.data) {
          const result = res.data;
          
          if (result.success && result.summary) {
            this.testSummaryForm.summary = result.summary;
            this.testSummaryForm.processingTime = result.processing_time;
            this.testSummaryForm.method = result.method;
            this.$message.success(`摘要生成成功！使用方法: ${result.method}`);
          } else {
            this.testSummaryForm.error = result.error_message || '摘要生成失败';
            this.$message.error('摘要生成失败：' + this.testSummaryForm.error);
          }
        } else {
          this.testSummaryForm.error = res?.message || '网络错误';
          this.$message.error('摘要测试失败：' + this.testSummaryForm.error);
        }
      } catch (error) {
        console.error('摘要测试失败:', error);
        this.testSummaryForm.error = error.message || '网络连接失败';
        this.$message.error('摘要测试失败，请检查网络连接和配置');
      } finally {
        this.testSummaryLoading = false;
      }
    },
    resetSummaryTest() {
      this.testSummaryForm.summary = '';
      this.testSummaryForm.processingTime = null;
      this.testSummaryForm.method = null;
      this.testSummaryForm.error = null;
    }
  }
};
</script>

<style scoped>

.my-tag {
    margin-bottom: 20px !important;
    width: 100%;
    text-align: left;
    background: var(--lightYellow);
    border: none;
    height: 40px;
    line-height: 40px;
    font-size: 16px;
    color: var(--black);
  }

  .el-tag {
    margin: 10px;
  }
  
.translation-management {
  min-height: calc(100vh - 60px);
}

/* 页面标题区域 */
.page-header {
  margin-bottom: 20px;
}

/* 配置容器 */
.config-container {
  border-radius: 5px;
}

.config-form {
  padding: 0;
}

/* 配置分组 */
.config-section {
  border-bottom: 1px solid #f7fafc;
}

.config-section:last-child {
  border-bottom: none;
}

.section-header {
  background: #fafafa;
  padding: 16px 24px;
  border-bottom: 1px solid #EBEEF5;
}

.section-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin: 0;
}

.section-content {
}



/* 表单元素 */
.full-width {
  width: 100%;
  max-width: 200px;
}

.number-input {
  width: 180px;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-form-item__label {
  font-weight: 500;
  color: #606266;
  font-size: 14px;
}

.input-field .el-input__inner,
.textarea-field .el-textarea__inner {
  border: 1px solid #DCDFE6;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 14px;
  transition: border-color 0.2s ease;
  background: #ffffff;
}

.input-field .el-input__inner:focus,
.textarea-field .el-textarea__inner:focus {
  border-color: #409EFF;
  outline: none;
}

.input-field .el-input__inner:hover,
.textarea-field .el-textarea__inner:hover {
  border-color: #C0C4CC;
}

/* 选择框选项 */
.option-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-content i {
  color: #4a5568;
  opacity: 0.8;
}

/* 表单提示 */
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  padding: 8px 12px;
  background: #F5F7FA;
  border-radius: 4px;
}

.custom-model-tip {
  background: #F5F7FA;
  color: #303133;
}

/* 自定义模型选择 */
.custom-model-select .el-input__inner {
  border-color: #409EFF !important;
  background: #F5F7FA !important;
}

/* 超时设置组 */
.timeout-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeout-input {
  width: 120px;
}

.timeout-input .el-input__inner {
  border: 1px solid #DCDFE6;
  border-radius: 4px 0 0 4px;
  background: #ffffff;
  transition: border-color 0.2s ease;
}

.timeout-input .el-input__inner:focus {
  border-color: #409EFF;
}

.timeout-input .el-input-group__append {
  background: #F5F7FA;
  border: 1px solid #DCDFE6;
  border-left: none;
  border-radius: 0 4px 4px 0;
  color: #909399;
  font-weight: 500;
  padding: 0 12px;
}

/* 信息面板 */
.info-panel {
  margin-top: 16px;
  padding: 16px;
  background: #F5F7FA;
  border-radius: 4px;
  border: 1px solid #EBEEF5;
}

.info-header {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
}

/* 操作按钮区域 */
.action-bar {
  padding: 20px 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* 测试对话框 */
.test-dialog .el-dialog {
  border-radius: 4px;
}

.dialog-content {
  padding: 24px;
}

.test-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 语言配置 */
.language-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  background: #F5F7FA;
  border-radius: 4px;
  border: 1px solid #EBEEF5;
}

.lang-display {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.lang-text {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  background: #ffffff;
  padding: 8px 16px;
  border-radius: 4px;
  border: 1px solid #DCDFE6;
  min-width: 60px;
  text-align: center;
}

.arrow-separator {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #409EFF;
  border-radius: 50%;
  color: white;
  font-size: 14px;
  font-weight: bold;
}

.lang-note {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  text-align: center;
}

/* 输入部分 */
.input-section,
.stream-mode,
.translate-section,
.result-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-section label,
.stream-mode label,
.result-section label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

/* 流式模式选择 */
.stream-options {
  display: flex;
  gap: 20px;
  margin-top: 8px;
}

.stream-radio .el-radio__label {
  font-weight: 400;
  color: #718096;
}

/* 翻译按钮 */
.translate-btn {
  width: 200px;
  padding: 12px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  background: #2d3748;
  border: none;
  color: white;
  transition: background-color 0.2s ease;
}

.translate-btn:hover {
  background: #1a202c;
}

.translate-btn i {
  margin-right: 6px;
}

/* 结果输出 */
.result-output .el-textarea__inner {
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
}

.result-meta {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: flex-start;
}

.result-meta .el-tag {
  font-size: 12px;
  border-radius: 4px;
}

.result-meta .el-tag i {
  margin-right: 4px;
}

/* 对话框底部 */
.dialog-footer {
  padding: 16px 24px;
  background: #f7fafc;
  text-align: right;
  border-top: 1px solid #e2e8f0;
}

.dialog-footer .el-button {
  padding: 8px 16px;
  border-radius: 6px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .translation-management {
    padding: 16px;
  }
  
  .title-section {
    padding: 20px;
  }
  
  .section-content {
    padding: 20px;
  }
  
  .language-info {
    flex-direction: column;
    gap: 12px;
  }
  
  .arrow-separator {
    transform: rotate(90deg);
  }
  
  .action-bar {
    padding: 16px;
    flex-direction: column;
  }
  
  .action-btn {
    width: auto;
    min-width: 120px;
  }
}

/* 覆盖Element UI样式 */
.el-form-item__label {
  padding-bottom: 6px !important;
}

.el-select .el-input__inner {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
}

.el-select .el-input__inner:focus {
  border-color: #2d3748 !important;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1) !important;
}

.el-input-number .el-input__inner {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
}

.el-input-number .el-input__inner:focus {
  border-color: #2d3748 !important;
  box-shadow: 0 0 0 3px rgba(45, 55, 72, 0.1) !important;
}

.el-radio__input.is-checked .el-radio__inner {
  border-color: #2d3748 !important;
  background: #2d3748 !important;
}

.el-radio__input.is-checked + .el-radio__label {
  color: #2d3748 !important;
}

/* 聊天测试弹窗相关样式 */
.el-dialog__wrapper {
  padding: 0;
}

.test-form .el-form-item {
  margin-bottom: 16px;
}

.test-form .el-form-item__label {
  font-weight: 500;
  color: #2d3748;
}

.test-translation-content {
  max-height: 300px;
  overflow-y: auto;
}

.test-translation-result {
  background-color: #f8f9fa;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  min-height: 60px;
  color: #2d3748;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.test-translation-result.empty {
  color: #a0aec0;
  font-style: italic;
}

.translation-meta {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #718096;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .translation-management {
    padding: 16px;
  }
  

  
  .form-actions {
    flex-direction: column;
  }
  
  .action-button {
    margin: 4px 0;
  }
}

/* 语言配置相关样式 */
.language-config-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}

.language-item {
  flex: 1;
  min-width: 200px;
}

.language-item .el-form-item__label {
  font-weight: 500;
  color: #2d3748;
}

.language-select {
  width: 100%;
  max-width: 200px;
}

.language-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 10px;
  color: #4299e1;
  font-size: 16px;
  min-width: 30px;
}

.language-arrow i {
  font-weight: bold;
  transform: scaleX(1.2);
}

/* 响应式语言配置 */
@media (max-width: 768px) {
  .language-config-row {
    flex-direction: column;
    gap: 16px;
  }
  
  .language-arrow {
    padding-top: 0;
    transform: rotate(90deg);
    height: 20px;
  }
  
  .language-item {
    min-width: 280px;
    max-width: 100%;
  }
}

/* 统一页面标题样式 */
.my-tag {
  margin-bottom: 20px !important;
  width: 100%;
  text-align: left;
  background: var(--lightYellow);
  border: none;
  height: 40px;
  line-height: 40px;
  font-size: 16px;
  color: var(--black);
}
</style>