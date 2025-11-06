<template>
  <div>
    <div v-if="!$common.isEmpty(article)">
      <!-- 封面 -->
      <div class="article-head my-animation-slide-top">
        <!-- 背景图片 -->
        <el-image class="article-image my-el-image"
                  v-once
                  lazy
                  :src="article.articleCover"
                  fit="cover">
          <div slot="error" class="image-slot" style="background-color: var(--lightGreen)">
          </div>
        </el-image>

        <!-- 语言切换按钮容器 -->
        <div class="language-switch-container">
          <!-- 动态语言切换按钮 -->
          <div class="article-language-switch" v-if="availableLanguageButtons.length > 1">
            <el-button-group>
              <el-button
                v-for="langButton in availableLanguageButtons"
                :key="langButton.code"
                :ref="`langBtn_${langButton.code}`"
                size="mini"
                :type="currentLang === langButton.code ? 'primary' : 'default'"
                @click.stop="handleLanguageSwitch(langButton.code)"
                @mousedown.stop="handleMouseDown"
                @touchstart.stop="handleTouchStart"
                :title="`切换到${langButton.name}`"
                :data-lang="langButton.code">
                {{langButton.name}}
              </el-button>
            </el-button-group>
          </div>
        </div>
        <!-- 文章信息 -->
        <div class="article-info-container">
          <div class="article-title">{{ articleTitle }}</div>
          <div class="article-info">
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path
                d="M510.4 65.5l259.69999999 0 1e-8 266.89999999c0 147.50000001-116.2 266.89999999-259.7 266.90000001-143.4 0-259.7-119.5-259.7-266.90000001 0.1-147.5 116.3-266.9 259.7-266.89999999z"
                fill="#FF9FCF"></path>
              <path
                d="M698.4 525.2l-13 0c53-48.4 86.5-117.8 86.5-195.20000001 0-10.2-0.7-20.3-1.8-30.19999999C613.8 377.50000001 438.6 444.9 266 437.7c15 33.4 36.7 63.1 63.5 87.5l-5.3 0c-122.6 0-225.5 88.1-248.8 204.1C340 677.2 597.7 609.2 862.2 585.7c-44.3-37.6-101.5-60.5-163.8-60.5z"
                fill="#FF83BB"></path>
              <path
                d="M862.2 585.7C597.7 609.2 340 677.2 75.4 729.3c-3.2 16.1-5 32.6-5 49.6 0 99.8 81.7 181.5 181.5 181.5l518.6 0c99.8 0 181.5-81.7 181.5-181.5 0.1-77.2-35-146.5-89.8-193.2z"
                fill="#FF5390"></path>
              <path
                d="M770.1 299.8C755.1 168 643.3 65.5 507.4 65.5c-146.1 0-264.5 118.4-264.5 264.5 0 38.4 8.3 74.8 23.1 107.7 172.6 7.2 347.8-60.2 504.1-137.9z"
                fill="#FF9FCF"></path>
              <path
                d="M436.4 282.1c0 24.1-19.6 43.7-43.7 43.7S349 306.2 349 282.1s19.6-43.7 43.7-43.7c24.19999999 0 43.7 19.6 43.7 43.7z"
                fill="#FFFFFF"></path>
              <path d="M625 282.1m-43.7 0a43.7 43.7 0 1 0 87.4 0 43.7 43.7 0 1 0-87.4 0Z" fill="#FFFFFF"></path>
            </svg>
            <span>&nbsp;{{ article.username }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path d="M512 512m-512 0a512 512 0 1 0 1024 0 512 512 0 1 0-1024 0Z" fill="#409EFF"></path>
              <path
                d="M654.222222 256c-17.066667 0-28.444444 11.377778-28.444444 28.444444v56.888889c0 17.066667 11.377778 28.444444 28.444444 28.444445s28.444444-11.377778 28.444445-28.444445v-56.888889c0-17.066667-11.377778-28.444444-28.444445-28.444444zM369.777778 256c-17.066667 0-28.444444 11.377778-28.444445 28.444444v56.888889c0 17.066667 11.377778 28.444444 28.444445 28.444445s28.444444-11.377778 28.444444-28.444445v-56.888889c0-17.066667-11.377778-28.444444-28.444444-28.444444z"
                fill="#FFFFFF"></path>
              <path
                d="M725.333333 312.888889H711.111111v28.444444c0 31.288889-25.6 56.888889-56.888889 56.888889s-56.888889-25.6-56.888889-56.888889v-28.444444h-170.666666v28.444444c0 31.288889-25.6 56.888889-56.888889 56.888889s-56.888889-25.6-56.888889-56.888889v-28.444444h-14.222222c-22.755556 0-42.666667 19.911111-42.666667 42.666667v341.333333c0 22.755556 19.911111 42.666667 42.666667 42.666667h426.666666c22.755556 0 42.666667-19.911111 42.666667-42.666667v-341.333333c0-22.755556-19.911111-42.666667-42.666667-42.666667zM426.666667 654.222222h-56.888889c-17.066667 0-28.444444-11.377778-28.444445-28.444444s11.377778-28.444444 28.444445-28.444445h56.888889c17.066667 0 28.444444 11.377778 28.444444 28.444445s-11.377778 28.444444-28.444444 28.444444z m227.555555 0h-56.888889c-17.066667 0-28.444444-11.377778-28.444444-28.444444s11.377778-28.444444 28.444444-28.444445h56.888889c17.066667 0 28.444444 11.377778 28.444445 28.444445s-11.377778 28.444444-28.444445 28.444444z m0-113.777778h-56.888889c-17.066667 0-28.444444-11.377778-28.444444-28.444444s11.377778-28.444444 28.444444-28.444444h56.888889c17.066667 0 28.444444 11.377778 28.444445 28.444444s-11.377778 28.444444-28.444445 28.444444z"
                fill="#FFFFFF"></path>
            </svg>
            <span>&nbsp;{{ article.createTime }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path d="M14.656 512a497.344 497.344 0 1 0 994.688 0 497.344 497.344 0 1 0-994.688 0z"
                    fill="#FF0000"></path>
              <path
                d="M374.976 872.64c-48.299-100.032-22.592-157.44 14.421-211.37 40.448-58.966 51.115-117.611 51.115-117.611s31.659 41.386 19.115 106.005c56.149-62.72 66.816-162.133 58.325-200.405 127.317 88.746 181.59 281.002 108.181 423.381C1016 652.501 723.093 323.2 672.277 285.867c16.939 37.333 20.054 100.032-14.101 130.474-58.027-219.84-201.664-265.002-201.664-265.002 16.96 113.536-61.781 237.397-137.344 330.24-2.816-45.163-5.632-76.544-29.483-119.808-5.333 82.176-68.373 149.269-85.29 231.445-22.912 111.637 17.237 193.173 170.581 279.424z"
                fill="#FFFFFF"></path>
            </svg>
            <span>&nbsp;{{ article.viewCount }}</span>
            <span>·</span>
            <svg viewBox="0 0 1024 1024" width="14" height="14" style="vertical-align: -2px;">
              <path
                d="M113.834667 291.84v449.194667a29.013333 29.013333 0 0 0 28.842666 29.013333h252.928v90.453333l160.597334-90.453333h252.928a29.013333 29.013333 0 0 0 29.013333-29.013333V291.84a29.013333 29.013333 0 0 0-29.013333-29.013333h-665.6a29.013333 29.013333 0 0 0-29.696 29.013333z"
                fill="#FFDEAD"></path>
              <path
                d="M809.130667 262.826667h-665.6a29.013333 29.013333 0 0 0-28.842667 29.013333v40.106667a29.013333 29.013333 0 0 1 28.842667-29.013334h665.6a29.013333 29.013333 0 0 1 29.013333 29.013334V291.84a29.013333 29.013333 0 0 0-29.013333-29.013333z"
                fill="#FFF3DB"></path>
              <path
                d="M556.202667 770.048h252.928a29.013333 29.013333 0 0 0 29.013333-29.013333V362.837333s-59.733333 392.533333-724.309333 314.709334v63.488a29.013333 29.013333 0 0 0 28.842666 29.013333h253.098667v90.453333z"
                fill="#F2C182"></path>
              <path
                d="M619.008 632.32l101.888-35.157333-131.754667-76.117334 29.866667 111.274667zM891.904 148.992a61.44 61.44 0 0 0-84.138667 22.528l-19.968 34.133333 106.666667 61.610667 19.968-34.133333a61.781333 61.781333 0 0 0-22.528-84.138667z"
                fill="#69BAF9"></path>
              <path d="M775.338667 198.775467l131.669333 76.032-186.026667 322.218666-131.6864-76.032z"
                    fill="#F7FBFF"></path>
              <path
                d="M775.168 198.826667l-5.290667 9.216 59.221334 34.133333a34.133333 34.133333 0 0 1 12.458666 46.592l-139.946666 242.346667a34.133333 34.133333 0 0 1-46.762667 12.629333l-59.050667-34.133333-6.656 11.434666 88.746667 51.2L720.896 597.333333l186.026667-322.56z"
                fill="#D8E3F0"></path>
              <path
                d="M616.448 622.592l2.56 9.728 101.888-35.157333-44.885333-25.941334-59.562667 51.370667zM891.904 148.992c-1.024 0-2.218667-0.853333-3.242667-1.536A61.610667 61.610667 0 0 1 887.466667 204.8l-19.968 34.133333-73.728-42.496-5.12 8.704 106.666666 61.610667 19.968-34.133333a61.781333 61.781333 0 0 0-23.381333-83.626667z"
                fill="#599ED4"></path>
              <path
                d="M265.898667 417.621333H494.933333a17.066667 17.066667 0 1 0 0-34.133333H265.898667a17.066667 17.066667 0 1 0 0 34.133333zM265.898667 533.504H494.933333a17.066667 17.066667 0 0 0 0-34.133333H265.898667a17.066667 17.066667 0 0 0 0 34.133333z"
                fill="#3D3D63"></path>
              <path
                d="M959.488 354.645333a99.84 99.84 0 0 0-23.722667-127.488 78.677333 78.677333 0 0 0-142.848-64.170666l-11.605333 20.138666a17.066667 17.066667 0 0 0-20.821333 7.168l-32.085334 55.466667H142.677333a46.250667 46.250667 0 0 0-45.909333 46.08v449.194667a46.08 46.08 0 0 0 45.909333 46.08h236.032v73.386666a17.066667 17.066667 0 0 0 8.362667 14.848 17.066667 17.066667 0 0 0 8.704 2.218667 17.066667 17.066667 0 0 0 8.362667-2.218667l156.672-88.234666h248.32a46.08 46.08 0 0 0 46.08-46.08V398.677333L921.6 283.306667a17.066667 17.066667 0 0 0-4.266667-21.504l1.877334-3.413334a65.365333 65.365333 0 0 1 10.410666 79.189334l-53.077333 91.989333a56.832 56.832 0 0 0 20.821333 77.653333 17.066667 17.066667 0 0 0 24.234667-6.314666 17.066667 17.066667 0 0 0-6.997333-23.04 23.04 23.04 0 0 1-8.362667-31.061334z m-138.410667 386.389334a11.946667 11.946667 0 0 1-11.946666 11.946666H556.202667a17.066667 17.066667 0 0 0-8.362667 2.218667l-134.997333 76.117333v-61.269333a17.066667 17.066667 0 0 0-17.066667-17.066667H142.677333a11.946667 11.946667 0 0 1-11.776-11.946666V291.84a11.946667 11.946667 0 0 1 11.776-11.946667h565.930667L574.464 512a17.066667 17.066667 0 0 0-1.706667 12.970667L597.333333 615.253333H265.898667a17.066667 17.066667 0 1 0 0 34.133334h352.938666a17.066667 17.066667 0 0 0 5.802667 0l102.4-35.328a17.066667 17.066667 0 0 0 9.216-7.509334l85.333333-147.968z m-204.8-184.661334l63.829334 36.864-49.322667 17.066667z m206.848-170.666666v1.365333l-108.373333 186.709333-102.4-59.050666L781.482667 221.866667l102.4 59.050666z m76.458667-161.28L887.466667 244.224l-76.970667-44.373333 11.264-19.797334a44.544 44.544 0 1 1 77.141333 44.544z"
                fill="#3D3D63"></path>
            </svg>
            <span>&nbsp;{{ article.commentCount }}</span>
          </div>
        </div>

        <div class="article-info-news"
             @click="weiYanDialogVisible = true"
             v-if="!$common.isEmpty(mainStore.currentUser) && mainStore.currentUser.id === article.userId">
          <svg width="30" height="30" viewBox="0 0 1024 1024">
            <path d="M0 0h1024v1024H0V0z" fill="#202425" opacity=".01"></path>
            <path
              d="M989.866667 512c0 263.918933-213.947733 477.866667-477.866667 477.866667S34.133333 775.918933 34.133333 512 248.081067 34.133333 512 34.133333s477.866667 213.947733 477.866667 477.866667z"
              fill="#FF7744"></path>
            <path
              d="M512 221.866667A51.2 51.2 0 0 1 563.2 273.066667v187.733333H750.933333a51.2 51.2 0 0 1 0 102.4h-187.733333V750.933333a51.2 51.2 0 0 1-102.4 0v-187.733333H273.066667a51.2 51.2 0 0 1 0-102.4h187.733333V273.066667A51.2 51.2 0 0 1 512 221.866667z"
              fill="#FFFFFF"></path>
          </svg>
        </div>
      </div>
      <!-- 文章 -->
      <div style="background: var(--background);">
        <div class="article-container my-animation-slide-bottom">
          <div v-if="!$common.isEmpty(article.videoUrl)" style="margin-bottom: 20px">
            <videoPlayer :url="{src: $common.decrypt(article.videoUrl)}"
                         :cover="article.articleCover">
            </videoPlayer>
          </div>

          <!-- 最新进展 -->
          <div v-if="!$common.isEmpty(treeHoleList)" class="process-wrap">
            <el-collapse accordion value="1">
              <el-collapse-item title="最新进展" name="1">
                <process :treeHoleList="treeHoleList" @deleteTreeHole="deleteTreeHole"></process>
              </el-collapse-item>
            </el-collapse>

            <hr>
          </div>

          <!-- 加载骨架 -->
          <div v-if="isLoading" class="entry-content">
            <el-skeleton :rows="10" animated />
          </div>
          <!-- 正文显示 -->
          <div v-else v-html="articleContentHtml" class="entry-content" :lang="currentLang" :key="articleContentKey"></div>
          <!-- 最后更新时间 -->
          <div class="article-update-time">
            <span>文章最后更新于 {{ article.updateTime }}</span>
          </div>
          <!-- 分类 -->
          <div class="article-sort">
            <span draggable="true" 
                  @dragstart="handleSortDragStart($event)"
                  @click="$router.push('/sort/' + article.sortId + '?labelId=' + article.labelId)">{{ article.sort.sortName +" · "+ article.label.labelName}}</span>
          </div>
          <!-- 作者信息 -->
          <blockquote>
            <div>
              作者：{{article.username}}
            </div>
            <div>
              <span>版权&许可请详阅</span>
              <span style="color: #38f;cursor: pointer"
                    @click="copyrightDialogVisible = true">
                版权声明
              </span>
            </div>
          </blockquote>
          <!-- 订阅和分享按钮 -->
          <div class="myCenter" id="article-like">
            <div class="subscribe-button" :class="{'subscribed': subscribe}" @click="subscribeLabel()">
              {{ subscribe ? '已订阅' : '订阅' }}
              <i class="el-icon-upload"></i>
            </div>
            <div class="share-card-button" @click="openShareCardDialog()">
              卡片分享
              <i class="el-icon-share"></i>
            </div>
          </div>

          <!-- 评论 -->
          <div v-if="article.commentStatus === true && enableComment">
            <comment :type="'article'" :source="article.id" :userId="article.userId"></comment>
          </div>
        </div>

        <div id="toc" class="toc"></div>
      </div>

      <div style="background: var(--background)">
        <myFooter></myFooter>
      </div>
    </div>



    <el-dialog title="版权声明"
               :visible.sync="copyrightDialogVisible"
               width="80%"
               :append-to-body="true"
               class="article-copy"
               custom-class="centered-dialog"
               center>
      <div style="display: flex;align-items: center;flex-direction: column">
        <el-avatar shape="square" :size="35" :src="$common.getAvatarUrl(mainStore.webInfo.avatar)">
          <img :src="$getDefaultAvatar()" />
        </el-avatar>
        <div class="copyright-container">
          <p>
            {{ mainStore.webInfo.webName }}是指运行在{{ $constant.host }}域名及相关子域名上的网站，本条款描述了{{ mainStore.webInfo.webName }}的网站版权声明：
          </p>
          <ul>
            <li>
              {{ mainStore.webInfo.webName }}提供的所有文章、展示的图片素材等内容部分来源于互联网平台，仅供学习参考。如有侵犯您的版权，请联系{{ mainStore.webInfo.webName }}负责人，{{ mainStore.webInfo.webName }}承诺将在一个工作日内改正。
            </li>
            <li>
              {{ mainStore.webInfo.webName }}不保证网站内容的全部准确性、安全性和完整性，请您在阅读、下载及使用过程中自行确认，{{ mainStore.webInfo.webName }}亦不承担上述资源对您造成的任何形式的损失或伤害。
            </li>
            <li>未经{{ mainStore.webInfo.webName }}允许，不得盗链、盗用本站内容和资源。</li>
            <li>
              {{ mainStore.webInfo.webName }}旨在为广大用户提供更多的信息；{{ mainStore.webInfo.webName }}不保证向用户提供的外部链接的准确性和完整性，该外部链接指向的不由本站实际控制的任何网页上的内容，{{ mainStore.webInfo.webName }}对其合法性亦概不负责，亦不承担任何法律责任。
            </li>
            <li>
              {{ mainStore.webInfo.webName }}中的文章/视频（包括转载文章/视频）的版权仅归原作者所有，若作者有版权声明或文章从其它网站转载而附带有原所有站的版权声明者，其版权归属以附带声明为准；文章仅代表作者本人的观点，与{{ mainStore.webInfo.webName }}立场无关。
            </li>
            <li>
              {{ mainStore.webInfo.webName }}自行编写排版的文章均采用
              <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" style="color: #38f;text-decoration: none;">
                知识共享署名-非商业性使用-相同方式共享 4.0 国际许可协议
              </a>
            </li>
            <li>
              许可协议标识：
              <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/">
                <img alt="知识共享许可协议"
                     src="https://i.creativecommons.org/l/by-nc-sa/4.0/88x31.png"
                     style="margin-top: 5px">
              </a>
            </li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <el-dialog title="最新进展"
               :visible.sync="weiYanDialogVisible"
               width="40%"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <div class="myCenter" style="margin-bottom: 20px">
          <el-date-picker
            v-model="newsTime"
            value-format="yyyy-MM-dd HH:mm:ss"
            type="datetime"
            align="center"
            placeholder="选择日期时间">
          </el-date-picker>
        </div>
        <commentBox :disableGraffiti="true"
                    @submitComment="submitWeiYan">
        </commentBox>
      </div>
    </el-dialog>

    <!-- 微信 -->
    <el-dialog title="密码"
               :modal="false"
               :visible.sync="showPasswordDialog"
               width="25%"
               :append-to-body="true"
               :close-on-click-modal="false"
               destroy-on-close
               center>
      <div>
        <div>
          <div class="password-content">{{tips}}</div>
        </div>
        <div style="margin: 20px auto">
          <el-input maxlength="30" v-model="password"></el-input>
        </div>
        <div style="display: flex;justify-content: center">
          <proButton :info="'提交'"
                     @click.native="submitPassword()"
                     :before="$constant.before_color_2"
                     :after="$constant.after_color_2">
          </proButton>
        </div>
      </div>
    </el-dialog>

    <!-- 卡片分享弹窗 -->
    <el-dialog title="卡片分享"
               :visible.sync="shareCardDialogVisible"
               width="500px"
               :append-to-body="true"
               custom-class="share-card-dialog centered-dialog"
               center>
      <div class="share-card-container">
        <!-- 卡片预览 -->
        <div class="share-card-preview" ref="shareCard" id="shareCard">
          <!-- 作者头像 -->
          <div class="card-avatar-container">
            <img :src="$common.getAvatarUrl(article.avatar)" 
                 alt="作者头像" 
                 class="card-avatar" />
          </div>
          
          <!-- 日期 -->
          <div class="card-date">
            {{ formatDate(article.createTime) }}
          </div>
          
          <!-- 标题 -->
          <div class="card-title">
            {{ articleTitle }}
          </div>
          
          <!-- 封面图片 -->
          <div class="card-cover">
            <img :src="article.articleCover" alt="文章封面" />
          </div>
          
          <!-- 底部信息 -->
          <div class="card-footer">
            <!-- 作者名 -->
            <div class="card-author">
              {{ article.username }}
            </div>
            
            <!-- 分隔线 -->
            <hr class="card-divider" />
            
            <!-- 品牌标识和二维码 -->
            <div class="card-bottom">
              <div class="card-brand">{{ mainStore.webInfo.webTitle || 'POETIZE' }}</div>
              <div class="card-qrcode" ref="qrcode"></div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 底部按钮 -->
      <div slot="footer" class="dialog-footer">
        <el-button @click="shareCardDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="downloadShareCard()">下载卡片</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
    import { useMainStore } from '@/stores/main';

const myFooter = () => import( "./common/myFooter");
  const comment = () => import( "./comment/comment");
  const process = () => import( "./common/process");
  const commentBox = () => import( "./comment/commentBox");
  const proButton = () => import( "./common/proButton");
  const videoPlayer = () => import( "./common/videoPlayer");
  import MarkdownIt from 'markdown-it';
  import axios from 'axios';
  import { getLanguageMapping, preloadLanguageMapping, getTocTitle } from '@/utils/languageUtils';
  // 导入资源加载器
  import { 
    loadMermaidResources, 
    isMermaidLoaded,
    loadEChartsResources,
    isEChartsLoaded,
    loadHighlightResources,
    isHighlightJsLoaded,
    loadClipboardResources,
    isClipboardLoaded,
    loadKatexResources,
    isKatexLoadedGlobal
  } from '@/components/live2d/utils/resourceLoader';

  export default {
    components: {
      myFooter,
      comment,
      commentBox,
      proButton,
      process,
      videoPlayer
    },

    data() {
      return {
        id: this.$route.params.id,
        lang: this.$route.params.lang,
        subscribe: false,
        article: {},
        articleContentHtml: "",
        articleContentKey: Date.now(), // 强制重新渲染的key
        treeHoleList: [],
        weiYanDialogVisible: false,
        copyrightDialogVisible: false,
        newsTime: "",
        showPasswordDialog: false,
        password: "",
        tips: "",
        scrollTop: 0,
        hasInitTocbot: false,
        metaTags: null,
        metaTagRetryCount: 0,
        isLoadingMeta: false,
        currentLang: 'zh', // 默认中文
        isLoading: false,
        translatedTitle: '',
        translatedContent: '',
        tempComment: null, // 存储临时评论内容
        targetLanguage: 'en', // 目标语言
        targetLanguageName: 'English', // 目标语言名称
        sourceLanguage: 'zh', // 源语言
        sourceLanguageName: '中文', // 源语言名称
        languageMap: {}, // 语言映射
        availableLanguages: [], // 文章实际可用的翻译语言
        availableLanguageButtons: [], // 动态生成的语言按钮列表
        shareCardDialogVisible: false, // 卡片分享弹窗显示状态
        tocbotRefreshed: false, // 标记tocbot是否已在首次滚动时刷新
        tocbotRefreshTimer: null, // tocbot刷新定时器
        loadingArticleId: null // 正在加载的文章ID（用于防止异步回调干扰）
      };
    },

    head() {
      if (!this.metaTags) {
        return {
          title: 'Poetize博客',
          meta: []
        };
      }
      
      return {
        title: this.metaTags.title,
        meta: [
          { name: 'description', content: this.metaTags.description },
          { name: 'keywords', content: this.metaTags.keywords },
          { name: 'author', content: this.metaTags.author },
          { property: 'og:title', content: this.metaTags.title },
          { property: 'og:description', content: this.metaTags.description },
          { property: 'og:type', content: 'article' },
          { property: 'og:url', content: this.metaTags['og:url'] },
          { property: 'og:image', content: this.metaTags['og:image'] },
          { name: 'twitter:card', content: this.metaTags['twitter:card'] },
          { name: 'twitter:title', content: this.metaTags.title },
          { name: 'twitter:description', content: this.metaTags.description },
          { name: 'twitter:image', content: this.metaTags['twitter:image'] },
          { property: 'article:published_time', content: this.metaTags['article:published_time'] },
          { property: 'article:modified_time', content: this.metaTags['article:modified_time'] }
        ]
      }
    },

    async created() {
        // 重置组件状态，防止缓存问题
        this.resetComponentState();
        
        // 先初始化语言映射（从数据库统一配置读取）
        this.languageMap = await getLanguageMapping();

        // 然后初始化语言设置，确保语言状态正确
        await this.initializeLanguageSettings();

        if (!this.$common.isEmpty(this.id)) {
          // 首次加载时强制清空预渲染内容，确保Vue重新渲染
          this.articleContentHtml = "";
          this.articleContentKey = Date.now();

          this.getArticle(localStorage.getItem("article_password_" + this.id));

          if ("0" !== localStorage.getItem("showSubscribe")) {
            this.$notify.success(
              '文章订阅',
              '点击文章下方订阅/取消订阅专栏',
              15000
            );
            // 设置延时关闭提示
            setTimeout(() => {
              localStorage.setItem("showSubscribe", "0");
            }, 3000);
          }
        }

        // 检查是否有待执行的订阅操作
        this.checkPendingSubscribe();

        // 文章页面加载时触发看板娘检查
        this.$nextTick(() => {
          // 延迟触发事件，确保页面元素已加载
          setTimeout(() => {
            if (document && document.dispatchEvent) {
              document.dispatchEvent(new Event('checkWaifu'));
            }
          }, 1000);
        });
      },

    mounted() {
      window.addEventListener("scroll", this.onScrollPage);
      // 注意：不在这里调用getTocbot()，因为文章内容还没加载
      // getTocbot()会在getArticle()完成后的$nextTick中调用

      // 监听主题切换事件
      this.$root.$on('themeChanged', this.handleThemeChange);

      // 添加全局事件委托处理语言切换按钮点击
      this.setupLanguageSwitchEventDelegation();
      
      // 注意：不需要实现JavaScript动态检测遮挡的响应式逻辑
      // 原因：通过CSS层叠上下文（.article-head z-index: 10 和 .article-container z-index: 1）
      // 已经彻底解决了语言切换按钮被遮挡的问题，无需动态调整按钮位置
      // 同时已注释掉 @media (max-width: 1050px) 中隐藏按钮的CSS规则
        
      // 添加看板娘初始化检查
      this.$nextTick(() => {
        // 检查当前配置是否启用看板娘
        const checkWaifuEnabled = () => {
          try {
            // 从本地存储获取配置
            const webInfoStr = localStorage.getItem('webInfo');
            if (webInfoStr) {
              const webInfoData = JSON.parse(webInfoStr);
              // 检查
              if (webInfoData.data) {
                return webInfoData.data.enableWaifu === true;
              }
            }
            return this.mainStore.webInfo.enableWaifu === true;
          } catch (e) {
            return false;
          }
        };
        
        // 检查是否已加载Live2D
        const checkLive2DLoaded = () => {
          return (
            typeof window.loadlive2d === 'function' && 
            document.getElementById('waifu') && 
            document.getElementById('live2d')
          );
        };
        
        // 检查看板娘是否显示
        const checkWaifuVisible = () => {
          const waifu = document.getElementById('waifu');
          return waifu && 
                waifu.style.display !== 'none' && 
                waifu.style.bottom !== '-500px';
        };
        
        // 检查并在需要时通过事件触发看板娘检查
        setTimeout(() => {
          if (checkWaifuEnabled()) {
            if (!checkLive2DLoaded() || !checkWaifuVisible()) {
              // 使用事件驱动方式加载看板娘，避免直接操作DOM
              if (!localStorage.getItem("waifu-display")) {
                // 触发检查事件，让live2d.js完成初始化
                document.dispatchEvent(new Event('checkWaifu'));
              }
            }
          }
        }, 2000); // 延迟2秒检查，确保页面完全加载
      });
      
      // 检查是否有临时保存的评论
      this.checkTempComment();

      // 检查是否有保存的页面状态
      this.checkPageState();

      // 监听路由变化，检查是否从登录页面返回
      this.$watch(() => this.$route.query, (newQuery) => {
        if (newQuery.hasComment === 'true') {
          // 从登录页面返回且带有评论标记
          this.$nextTick(() => {
            this.checkTempComment();
          });
        }

        // 检查回复操作恢复标记
        if (newQuery.hasReplyAction === 'true') {
          // 从登录页面返回且带有回复操作标记
          this.$nextTick(() => {
            this.checkPageState();
          });
        }
      });
    },

    destroyed() {
      window.removeEventListener("scroll", this.onScrollPage);

      // 移除主题切换事件监听
      this.$root.$off('themeChanged', this.handleThemeChange);

      // 清理语言切换事件监听器
      if (this.languageSwitchHandler) {
        document.removeEventListener('click', this.languageSwitchHandler, true);
        document.removeEventListener('touchend', this.languageSwitchHandler, true);
        document.removeEventListener('mousedown', this.languageSwitchHandler, true);
        document.removeEventListener('touchstart', this.languageSwitchHandler, true);
        this.languageSwitchHandler = null;
      }

      // 清理FAB点击外部区域事件监听器
      if (this.fabClickOutsideHandler) {
        document.removeEventListener('click', this.fabClickOutsideHandler, true);
        this.fabClickOutsideHandler = null;
      }
    },

    watch: {
      scrollTop(scrollTop, oldScrollTop) {
        // 滚动监听逻辑已移至home.vue的toolButton控制
      },
      '$route.params': function(newParams, oldParams) {
        // 检查文章ID或语言参数是否变化
        const newId = newParams.id;
        const oldId = oldParams.id;
        const newLang = newParams.lang;
        const oldLang = oldParams.lang;
        
        if (newId && newId !== this.id) {
          // 重置组件状态，防止显示旧数据
          this.resetComponentState();
          
          // 更新组件的id和lang数据
          this.id = newId;
          this.lang = newLang;

          // 重新初始化语言设置 - 关键修复：确保每次切换文章都重新初始化语言
          this.initializeLanguageSettings().then(() => {
            // 语言初始化完成后再获取文章
            const password = localStorage.getItem("article_password_" + this.id);
            this.getArticle(password);
          }).catch(error => {
            // 即使语言初始化失败，也要获取文章
            const password = localStorage.getItem("article_password_" + this.id);
            this.getArticle(password);
          });

          // 检查是否有待执行的订阅操作
          this.$nextTick(() => {
            this.checkPendingSubscribe();
          });
        } else if (newId === this.id && newLang !== oldLang) {
          // 同一文章，仅语言参数变化
          this.lang = newLang;
          
          if (newLang && this.languageMap[newLang]) {
            if (this.currentLang !== newLang) {
              this.switchLanguage(newLang);
            }
          } else {
            // 如果语言参数无效，切换到默认源语言
            this.switchLanguage(this.sourceLanguage);
          }
        }
      },

    },

    computed: {
      mainStore() {
        return useMainStore();
      },
      articleTitle() {
        // 如果当前语言不是源语言且已有翻译标题，则显示翻译标题，否则显示原始标题
        return (this.currentLang !== this.sourceLanguage && this.translatedTitle) ? this.translatedTitle : this.article.articleTitle;
      },
      
      // 全局评论开关 - 从系统配置中读取
      enableComment() {
        const sysConfig = this.mainStore.sysConfig;
        // 默认为 true，如果配置不存在或配置值为 'true' 则显示评论
        if (!sysConfig || !sysConfig.enableComment) {
          return true;
        }
        return sysConfig.enableComment === 'true' || sysConfig.enableComment === true;
      },
      
      // 对话框居中由 centered-dialog.css 全局样式处理（移除了自定义top计算）
      shareCardDialogTop() {
        return '15vh'; // 保留作为fallback，但实际由centered-dialog.css的flex居中处理
      }
    },

    beforeDestroy() {
      // 组件销毁时清理状态，防止影响下一个文章组件
      this.clearComponentState();
      
      // 销毁tocbot实例
      if (window.tocbot) {
        try {
          window.tocbot.destroy();
        } catch (e) {
          // 忽略销毁失败
        }
      }
      
      // 清理定时器
      if (this.tocbotRefreshTimer) {
        clearTimeout(this.tocbotRefreshTimer);
        this.tocbotRefreshTimer = null;
      }
    },

    methods: {
      // 处理分类标签拖拽开始事件
      handleSortDragStart(event) {
        // 构建分类页面的完整URL
        const baseUrl = window.location.origin;
        const sortPath = `/sort/${this.article.sortId}?labelId=${this.article.labelId}`;
        const sortUrl = `${baseUrl}${sortPath}`;
        
        // 设置拖拽数据
        event.dataTransfer.effectAllowed = 'link';
        event.dataTransfer.setData('text/uri-list', sortUrl);
        event.dataTransfer.setData('text/plain', sortUrl);
        
        // 设置拖拽时显示的文本
        const title = `${this.article.sort.sortName} · ${this.article.label.labelName}`;
        event.dataTransfer.setData('text/html', `<a href="${sortUrl}">${title}</a>`);
      },
      
      // 重置组件状态，防止缓存问题
      resetComponentState() {
        this.article = {};
        this.translatedTitle = '';
        this.translatedContent = '';
        this.articleContentHtml = '';
        this.articleContentKey = Date.now();
        this.isLoading = false;
        
        // 重置语言相关状态 - 这是关键修复
        this.currentLang = this.sourceLanguage || 'zh';
        this.availableLanguages = [];
        this.availableLanguageButtons = [];
        
        // 重置密码相关状态
        this.showPasswordDialog = false;
        this.password = '';
        this.tips = '';
        
        // 重置订阅状态
        this.subscribe = false;
        
        // 重置元标签相关状态
        this.metaTags = null;
        this.metaTagRetryCount = 0;
        this.isLoadingMeta = false;
        
        // 重置目录相关状态
        this.tocbotRefreshed = false;
        if (this.tocbotRefreshTimer) {
          clearTimeout(this.tocbotRefreshTimer);
          this.tocbotRefreshTimer = null;
        }
        
        // 重置正在加载的文章ID（防止旧文章的异步回调影响新文章）
        this.loadingArticleId = null;
        
        // 销毁旧的tocbot实例（路由切换时）
        if (window.tocbot) {
          try {
            window.tocbot.destroy();
          } catch (e) {
            // 忽略销毁失败的错误
          }
        }
      },

      // 清理组件状态
      clearComponentState() {
        // 清理其他可能的异步操作
        this.loading = false;
        
        // 清理翻译内容
        this.translatedTitle = '';
        this.translatedContent = '';
      },

      subscribeLabel() {
        // 首先显示确认订阅对话框
        const confirmMessage = this.subscribe
          ? '确认取消订阅专栏【' + this.article.label.labelName + '】？'
          : '确认订阅专栏【' + this.article.label.labelName + '】？订阅专栏后，该专栏发布新文章将通过邮件通知订阅用户。';

        const confirmTitle = this.subscribe ? "取消订阅" : "文章订阅";

        this.$confirm(confirmMessage, confirmTitle, {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          center: true
        }).then(() => {
          // 用户确认订阅意图后，检查登录状态
          if (this.$common.isEmpty(this.mainStore.currentUser)) {
            // 未登录，显示登录提示并立即跳转到登录页面
            this.$message({
              message: "请先登录！",
              type: "error"
            });

            // 立即保存订阅意图并跳转到登录页面
            this.saveSubscribeIntentAndRedirectToLogin();
            return;
          }

          // 已登录，直接执行订阅操作
          this.executeSubscribe();
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消!'
          });
        });
      },

      // 保存订阅意图并跳转到登录页面
      saveSubscribeIntentAndRedirectToLogin() {
        const subscribeIntent = {
          articleId: this.id,
          labelId: this.article.labelId,
          labelName: this.article.label.labelName,
          action: this.subscribe ? 'unsubscribe' : 'subscribe',
          timestamp: Date.now()
        };

        // 保存订阅意图到localStorage
        localStorage.setItem('pendingSubscribe', JSON.stringify(subscribeIntent));

        // 使用统一的登录跳转函数
        this.$common.redirectToLogin(this.$router, {
          message: '请先登录！'
        }, this);
      },

      // 执行订阅操作
      executeSubscribe() {
        this.$http.get(this.$constant.baseURL + "/user/subscribe", {
          labelId: this.article.labelId,
          flag: !this.subscribe
        })
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              this.mainStore.loadCurrentUser( res.data);
            }
            this.subscribe = !this.subscribe;

            // 显示成功消息
            const message = this.subscribe ? '订阅成功！' : '取消订阅成功！';
            this.$message({
              message: message,
              type: "success"
            });
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },

      // 检查并处理待执行的订阅操作
      checkPendingSubscribe() {
        const pendingSubscribe = localStorage.getItem('pendingSubscribe');
        if (!pendingSubscribe) {
          return;
        }

        try {
          const subscribeIntent = JSON.parse(pendingSubscribe);

          // 检查是否是当前文章的订阅意图
          if (subscribeIntent.articleId === this.id) {
            // 清除待执行的订阅意图
            localStorage.removeItem('pendingSubscribe');

            // 检查用户是否已登录
            if (!this.$common.isEmpty(this.mainStore.currentUser)) {
              // 延迟执行订阅操作，确保页面数据已加载完成
              this.$nextTick(() => {
                setTimeout(() => {
                  this.executeSubscribe();
                }, 500);
              });
            }
          }
        } catch (error) {
          localStorage.removeItem('pendingSubscribe');
        }
      },
      submitPassword() {
        if (this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请先输入密码！",
            type: "error"
          });
          return;
        }

        this.getArticle(this.password);
      },
      deleteTreeHole(id) {
        if (this.$common.isEmpty(this.mainStore.currentUser)) {
          // 使用统一的登录跳转函数
          this.$common.redirectToLogin(this.$router, {
            message: '请先登录！'
          }, this);
          return;
        }

        this.$confirm('确认删除？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'success',
          center: true
        }).then(() => {
          this.$http.get(this.$constant.baseURL + "/weiYan/deleteWeiYan", {id: id})
            .then((res) => {
              this.$message({
                type: 'success',
                message: '删除成功!'
              });
              this.getNews();
            })
            .catch((error) => {
              this.$message({
                message: error.message,
                type: "error"
              });
            });
        }).catch(() => {
          this.$message({
            type: 'success',
            message: '已取消删除!'
          });
        });
      },
      submitWeiYan(content) {
        let weiYan = {
          content: content,
          createTime: this.newsTime,
          source: this.article.id
        };

        this.$http.post(this.$constant.baseURL + "/weiYan/saveNews", weiYan)
          .then((res) => {
            this.weiYanDialogVisible = false;
            this.newsTime = "";
            this.getNews();
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      getNews() {
        this.$http.post(this.$constant.baseURL + "/weiYan/listNews", {
          current: 1,
          size: 9999,
          source: this.article.id
        })
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              res.data.records.forEach(c => {
                c.content = c.content.replace(/\n{2,}/g, '<div style="height: 12px"></div>');
                c.content = c.content.replace(/\n/g, '<br/>');
                c.content = this.$common.faceReg(c.content);
                c.content = this.$common.pictureReg(c.content);
              });
              this.treeHoleList = res.data.records;
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      },
      onScrollPage() {
        this.scrollTop = document.documentElement.scrollTop || document.body.scrollTop;
        const tocElements = document.querySelectorAll('.toc');
        tocElements.forEach(element => {
          if (this.scrollTop < (window.innerHeight / 4)) {
            element.style.top = (window.innerHeight / 4) + 'px';
          } else {
            element.style.top = '90px';
          }
        });
        
        // 在用户首次滚动时刷新tocbot，确保位置计算准确
        if (!this.tocbotRefreshed && window.tocbot && window.tocbot.refresh) {
          if (this.tocbotRefreshTimer) {
            clearTimeout(this.tocbotRefreshTimer);
          }
          this.tocbotRefreshTimer = setTimeout(() => {
            if (window.tocbot && window.tocbot.refresh) {
              window.tocbot.refresh();
              this.tocbotRefreshed = true;
            }
          }, 50);
        }
      },
      getTocbot() {
        // 检查是否有旧内容（用于判断是否需要过渡效果）
        const tocContainer = document.getElementById('toc');
        const hasOldContent = tocContainer && tocContainer.children.length > 0;
        
        // 如果有旧内容，添加过渡效果避免闪烁
        if (hasOldContent) {
          const tocElements = document.querySelectorAll('.toc');
          tocElements.forEach(el => {
            el.style.transition = 'opacity 0.15s ease-out';
            el.style.opacity = '0.3'; // 降低透明度而不是完全隐藏
          });
        }
        
        // 销毁之前的实例
        if (window.tocbot) {
          try {
            window.tocbot.destroy();
          } catch (e) {
          }
        }

        const initTocbot = () => {
          this.$nextTick(() => {
            // 验证DOM元素
            const entryContent = document.querySelector('.entry-content');
            if (!entryContent) {
              setTimeout(() => initTocbot(), 50);
              return;
            }
            
            const headings = entryContent.querySelectorAll('h1, h2, h3, h4, h5');
            if (headings.length === 0) {
              setTimeout(() => initTocbot(), 50);
              return;
            }
            
            if (window.tocbot) {
              try {
                window.tocbot.destroy();
                
                // 初始化目录
                window.tocbot.init({
                  tocSelector: '#toc',
                  contentSelector: '.entry-content',
                  headingSelector: 'h1, h2, h3, h4, h5',
                  scrollSmooth: true,
                  fixedSidebarOffset: 'auto',
                  scrollSmoothOffset: -100,
                  hasInnerContainers: false,
                  headingsOffset: 100,
                  scrollSmoothDuration: 420,
                  includeHtml: false,
                  onClick: function(e) {
                    e.preventDefault();
                  }
                });
                
                // 动态设置目录标题（根据当前语言）
                this.$nextTick(() => {
                  const tocElement = document.querySelector('.toc');
                  if (tocElement) {
                    const tocTitle = getTocTitle(this.currentLang || 'zh');
                    tocElement.setAttribute('data-toc-title', `🏖️${tocTitle}`);
                  }
                });
                
                // 强制重排并刷新
                const forceReflow = () => {
                  const toc = document.getElementById('toc');
                  const content = document.querySelector('.entry-content');
                  if (toc) void toc.offsetHeight;
                  if (content) void content.offsetHeight;
                };
                
                this.$nextTick(() => {
                  forceReflow();
                  if (window.tocbot && window.tocbot.refresh) {
                    window.tocbot.refresh();
                  }
                  
                  // 目录初始化完成，恢复显示
                  requestAnimationFrame(() => {
                    const tocElements = document.querySelectorAll('.toc');
                    tocElements.forEach(el => {
                      el.style.transition = 'opacity 0.2s ease-in';
                      el.style.opacity = '1';
                    });
                  });
                });
              } catch (e) {
                // 即使失败也要恢复显示，避免目录永久半透明
                const tocElements = document.querySelectorAll('.toc');
                tocElements.forEach(el => {
                  el.style.opacity = '1';
                });
              }
            } else {
              // tocbot还未加载，延迟重试
              setTimeout(() => initTocbot(), 50);
            }
          });
        };

        // 加载并初始化tocbot
        if (window.tocbot) {
          initTocbot();
        } else {
          const existingScript = document.querySelector(`script[src="${this.$constant.tocbot}"]`);
          if (existingScript) {
            existingScript.addEventListener('load', initTocbot);
          } else {
            let script = document.createElement('script');
            script.type = 'text/javascript';
            script.src = this.$constant.tocbot;
            script.onload = initTocbot;
            script.onerror = () => {}; // 忽略加载失败
            
            const head = document.getElementsByTagName('head')[0];
            if (script && script.nodeType === Node.ELEMENT_NODE && head && typeof head.appendChild === 'function') {
              try {
                head.appendChild(script);
              } catch (e) {
              }
            }
          }
        }
        
        // 移动端隐藏目录
        if (this.$common.mobile()) {
          this.$nextTick(() => {
            const tocElements = document.querySelectorAll('.toc');
            tocElements.forEach(element => {
              element.style.display = 'none';
            });
          });
        }
      },
      addId() {
        const entryContent = document.querySelector('.entry-content');
        if (entryContent) {
          const headings = entryContent.querySelectorAll('h1, h2, h3, h4, h5, h6');
          headings.forEach((heading, index) => {
            if (!heading.id) {
              heading.id = 'toc-' + index;
            }
          });
        }
      },
      getArticleMeta() {
        this.isLoadingMeta = true;
        const timeout = setTimeout(() => {
          if (this.isLoadingMeta) {
            this.isLoadingMeta = false;
            this.setDefaultMetaTags();
          }
        }, 3000);
        
        // 使用带noCount参数的API，避免增加热度
        this.$http.get(this.$constant.baseURL + "/article/getArticleByIdNoCount", {id: this.id})
          .then((articleRes) => {
            if (articleRes.code === 200 && articleRes.data) {
              // 文章信息获取成功后再获取SEO元数据
              axios.get(this.$constant.baseURL + `/seo/getArticleMeta?articleId=${this.id}&lang=${this.currentLang}`)
                .then((res) => {
                  clearTimeout(timeout);
                  this.isLoadingMeta = false;
                  
                  if (res.data && res.data.code === 200 && res.data.data) {
                    this.metaTags = res.data.data;
                    this.updateMetaTags();
                  } else {
                    console.error('获取文章元标签失败, 服务返回错误:', res.data ? (res.data.message || '未知错误') : '返回数据为空');
                    this.setDefaultMetaTags();
                  }
                })
                .catch((error) => {
                  clearTimeout(timeout);
                  this.isLoadingMeta = false;
                  console.error('获取文章元标签失败:', error);
                  
                  // 添加简单的自动重试，最多重试2次
                  if (!this.metaTagRetryCount || this.metaTagRetryCount < 2) {
                    this.metaTagRetryCount = (this.metaTagRetryCount || 0) + 1;
                    setTimeout(() => {
                      this.getArticleMeta();
                    }, 1500); // 1.5秒后重试
                  } else {
                    // 重试失败，使用默认元标签
                    this.setDefaultMetaTags();
                  }
                });
            } else {
              clearTimeout(timeout);
              this.isLoadingMeta = false;
              console.error('获取文章信息失败，无法获取元标签');
              this.setDefaultMetaTags();
            }
          })
          .catch((error) => {
            clearTimeout(timeout);
            this.isLoadingMeta = false;
            console.error('获取文章信息失败:', error);
            this.setDefaultMetaTags();
          });
      },
      setDefaultMetaTags() {
        if (this.article) {
          this.metaTags = {
            title: this.article.articleTitle || 'Poetize博客',
            description: this.article.articleTitle ? (this.article.articleTitle + ' - Poetize博客') : 'Poetize博客',
            keywords: 'Poetize,博客,个人网站',
            author: this.article.username || 'Admin',
            'og:url': window.location.href,
            'og:image': this.article.articleCover || '',
            'twitter:card': 'summary',
            'article:published_time': this.article.createTime || '',
            'article:modified_time': this.article.updateTime || ''
          };
          this.updateMetaTags();
        }
      },
      updateMetaTags() {
        if (!this.metaTags) return;
        
        // 不再设置document.title，保持网站统一标题
        // document.title = this.metaTags.title;
        // window.OriginTitile = this.metaTags.title;
        
        document.querySelectorAll('meta[data-vue-meta="true"]').forEach(el => el.remove());
        
        const addMetaTag = (name, content, isProperty = false) => {
          if (!content) return;
          
          const meta = document.createElement('meta');
          if (isProperty) {
            meta.setAttribute('property', name);
          } else {
            meta.setAttribute('name', name);
          }
          meta.setAttribute('content', content);
          meta.setAttribute('data-vue-meta', 'true');
          // 安全地添加meta元素到head
          if (meta && meta.nodeType === Node.ELEMENT_NODE && document.head && typeof document.head.appendChild === 'function') {
            try {
              document.head.appendChild(meta);
            } catch (e) {
            }
          }
        };
        
        addMetaTag('description', this.metaTags.description);
        addMetaTag('keywords', this.metaTags.keywords);
        addMetaTag('author', this.metaTags.author);
        addMetaTag('og:title', this.metaTags.title, true);
        addMetaTag('og:description', this.metaTags.description, true);
        addMetaTag('og:type', 'article', true);
        addMetaTag('og:url', this.metaTags['og:url'], true);
        addMetaTag('og:image', this.metaTags['og:image'], true);
        addMetaTag('twitter:card', this.metaTags['twitter:card']);
        addMetaTag('twitter:title', this.metaTags.title);
        addMetaTag('twitter:description', this.metaTags.description);
        addMetaTag('twitter:image', this.metaTags['twitter:image']);
        addMetaTag('article:published_time', this.metaTags['article:published_time'], true);
        addMetaTag('article:modified_time', this.metaTags['article:modified_time'], true);
      },
      getArticle(password) {
        this.isLoading = true;
        
        // 设置正在加载的文章ID（在this.id更新之后调用，所以this.id已经是新文章ID）
        this.loadingArticleId = this.id;
        
        // 重置状态，防止显示旧数据
        this.article = {};
        this.articleContentHtml = '';
        this.translatedTitle = '';
        this.translatedContent = '';
        this.tocbotRefreshed = false; // 重置tocbot刷新标志
        
        // 使用Promise.all并行处理所有请求
        // 如果当前语言不是源语言，在第一次请求时就带上语言参数
        const articleParams = {id: this.id, password: password};
        if (this.currentLang && this.currentLang !== this.sourceLanguage) {
          articleParams.language = this.currentLang;
        }
        
        Promise.all([
          this.$http.get(this.$constant.baseURL + "/article/getArticleById", articleParams),
          this.$http.post(this.$constant.baseURL + "/weiYan/listNews", { current: 1, size: 9999, source: this.id }),
          this.fetchArticleMeta()
        ])
        .then(async ([articleRes, newsRes]) => {
          // 处理文章数据
          if (!this.$common.isEmpty(articleRes.data)) {
            this.article = articleRes.data;
            
            // 检查当前语言状态，决定显示内容

            const md = new MarkdownIt({breaks: true})
              .use(require('markdown-it-multimd-table'))
              .use(require('@iktakahiro/markdown-it-katex'));

            // 判断显示原文还是翻译
            if (this.currentLang !== this.sourceLanguage && this.article.translatedContent) {
              // 显示翻译内容（后端已一次性返回）
              this.translatedTitle = this.article.translatedTitle;
              this.translatedContent = this.article.translatedContent;
              this.articleContentHtml = md.render(this.translatedContent);
            } else {
              // 显示原文
              this.translatedTitle = '';
              this.translatedContent = '';
              this.articleContentHtml = md.render(this.article.articleContent);
            }
            
            this.articleContentKey = Date.now();
            
            // 等待DOM渲染完成后，再检测并加载资源
            this.$nextTick(() => {
              this.$common.imgShow(".entry-content img");
              this.highlight();
              this.renderMermaid();
              this.renderECharts();
              this.addId();
              
              // 在内容渲染到DOM后检测资源并初始化目录
              // 注意：getTocbot()会在detectAndLoadResources()中调用
              this.detectAndLoadResources();
            });

            // 确保样式正确应用的保险措施
            setTimeout(() => {
              // 检查是否有代码块没有正确处理
              const entryContent = document.querySelector('.entry-content');
              if (entryContent) {
                const unprocessedBlocks = entryContent.querySelectorAll('pre:not(.highlight-wrap)');
                if (unprocessedBlocks.length > 0) {
                  this.highlight();
                  this.renderMermaid();
                  this.renderECharts();
                }
              }
            }, 1000);

            if (!this.$common.isEmpty(password)) {
              localStorage.setItem("article_password_" + this.id, password);
            }
            this.showPasswordDialog = false;
            if (!this.$common.isEmpty(this.mainStore.currentUser) && !this.$common.isEmpty(this.mainStore.currentUser.subscribe)) {
              this.subscribe = JSON.parse(this.mainStore.currentUser.subscribe).includes(this.article.labelId);
            }

            // 获取文章可用的翻译语言并生成动态按钮
            this.getArticleAvailableLanguages();
          } else {
            // 文章数据为空，说明文章不存在，跳转到404页面
            this.$router.push('/404');
            return;
          }

          // 处理"最新进展"数据
          if (!this.$common.isEmpty(newsRes.data)) {
            newsRes.data.records.forEach(c => {
              c.content = c.content.replace(/\n{2,}/g, '<div style="height: 12px"></div>');
              c.content = c.content.replace(/\n/g, '<br/>');
              c.content = this.$common.faceReg(c.content);
              c.content = this.$common.pictureReg(c.content);
            });
            this.treeHoleList = newsRes.data.records;
          }
        })
        .catch(error => {
          console.error('获取文章失败:', error);
          
          // 统一错误处理
          if (error && error.message && "密码错误" === error.message.substr(0, 4)) {
            // 密码错误，显示密码输入框
            if (!this.$common.isEmpty(password)) {
              localStorage.removeItem("article_password_" + this.id);
              this.$message({
                message: "密码错误，请重新输入！",
                type: "error",
                customClass: "message-index"
              });
            }
            this.tips = error.message.substr(4);
            this.showPasswordDialog = true;
          } else if (error && error.message && (
            error.message.includes('文章不存在') || 
            error.message.includes('文章未找到') ||
            error.message.includes('404') ||
            error.message.includes('Not Found')
          )) {
            // 文章不存在，跳转到404页面
            this.$router.push('/404');
            return;
          } else {
            // 其他错误（网络错误等），显示错误消息但不跳转
            this.$message({
              message: error ? error.message : '加载失败，请重试',
              type: "error",
              customClass: "message-index"
            });
            
          }
        })
        .finally(() => {
          this.isLoading = false;
        });
      },
      fetchArticleMeta() {
        return new Promise((resolve, reject) => {
          this.isLoadingMeta = true;
          const timeout = setTimeout(() => {
            if (this.isLoadingMeta) {
              this.isLoadingMeta = false;
              this.setDefaultMetaTags();
              resolve(); 
            }
          }, 3000);

          axios.get(this.$constant.baseURL + `/seo/getArticleMeta?articleId=${this.id}&lang=${this.currentLang}`)
            .then((res) => {
              clearTimeout(timeout);
              this.isLoadingMeta = false;
              
              if (res.data && res.data.code === 200 && res.data.data) {
                this.metaTags = res.data.data;
                this.updateMetaTags();
              } else {
                console.error('获取文章元标签失败, 服务返回错误:', res.data ? (res.data.message || '未知错误') : '返回数据为空');
                this.setDefaultMetaTags();
              }
              resolve();
            })
            .catch((error) => {
              clearTimeout(timeout);
              this.isLoadingMeta = false;
              console.error('获取文章元标签失败:', error);
              this.setDefaultMetaTags();
              // 在Promise中，我们应该resolve而不是reject，因为这不算关键路径失败
              resolve();
            });
        });
      },
      highlight() {
        // 如果 hljs 未加载，静默返回（等待按需加载完成后再调用）
        if (!isHighlightJsLoaded()) {
          return;
        }

        let attributes = {
          autocomplete: "off",
          autocorrect: "off",
          autocapitalize: "off",
          spellcheck: "false",
          contenteditable: "false"
        };

        const entryContent = document.querySelector('.entry-content');
        if (!entryContent) return;
        
        const preElements = entryContent.querySelectorAll('pre');
        preElements.forEach((item, i) => {
          // 避免重复处理已经处理过的代码块
          if (item.classList.contains('highlight-wrap')) {
            return;
          }

          const preCode = item.querySelector('code');
          if (!preCode) {
            return; // 没有code子元素，跳过
          }

          let classNameStr = preCode.className || "";
          let classNameArr = classNameStr.split(" ");

          let lang = "";
          classNameArr.some(function (className) {
            if (className.indexOf("language-") > -1) {
              lang = className.substring(className.indexOf("-") + 1, className.length);
              return true;
            }
          });
          
          // 跳过Mermaid代码块，由renderMermaid处理
          if (lang === 'mermaid') {
            return;
          }
          
          // 跳过ECharts代码块，由renderECharts处理
          if (lang === 'echarts') {
            return;
          }

          try {
            let language = hljs.getLanguage(lang.toLowerCase());
            if (language === undefined) {
              let autoLanguage = hljs.highlightAuto(preCode.textContent);
              preCode.classList.remove("language-" + lang);
              lang = autoLanguage.language;
              if (lang === undefined) {
                lang = "java";
              }
              preCode.classList.add("language-" + lang);
            } else {
              lang = language.name;
            }

            // 移除 loading 状态
            item.classList.remove('code-loading');
            item.classList.add("highlight-wrap");
            // 设置属性
            Object.keys(attributes).forEach(key => {
              item.setAttribute(key, attributes[key]);
            });
            preCode.setAttribute("data-rel", lang.toUpperCase());
            preCode.classList.add(lang.toLowerCase());
            
            // 使用推荐的highlightElement方法替代废弃的highlightBlock
            if (typeof hljs.highlightElement === 'function') {
              hljs.highlightElement(preCode);
            } else if (typeof hljs.highlightBlock === 'function') {
              hljs.highlightBlock(preCode);
            }
            
            // 使用CSS计数器添加行号（替代hljs.lineNumbersBlock插件）
            this.addLineNumbersWithCSS(preCode);
          } catch (error) {
            console.error('Error highlighting code block:', error);
            // 即使高亮失败，也要保证基本样式
            item.classList.add("highlight-wrap");
            Object.keys(attributes).forEach(key => {
              item.setAttribute(key, attributes[key]);
            });
            preCode.setAttribute("data-rel", lang.toUpperCase());
            preCode.classList.add(lang.toLowerCase());
          }
        });

        // 处理复制按钮，避免重复添加
        const codeBlocks = entryContent.querySelectorAll('pre code');
        codeBlocks.forEach((block, i) => {
          // 检查是否已经有复制按钮
          if (block.nextElementSibling && block.nextElementSibling.classList.contains('copy-code')) {
            return; // 已经有复制按钮了
          }

          block.id = "hljs-" + i;

          // 创建复制按钮
          const copyButton = document.createElement('a');
          copyButton.className = 'copy-code';
          copyButton.href = 'javascript:';
          copyButton.setAttribute('data-clipboard-target', '#hljs-' + i);
          copyButton.innerHTML = '<i class="fa fa-clipboard" aria-hidden="true"></i>';
          
          // 插入复制按钮
          // 安全地插入复制按钮
          if (block.parentNode && copyButton && copyButton.nodeType === Node.ELEMENT_NODE) {
            try {
              block.parentNode.insertBefore(copyButton, block.nextSibling);
            } catch (e) {
            }
          }
        });
        
        // 初始化剪贴板功能
        if (typeof ClipboardJS !== 'undefined') {
          const that = this; // 保存Vue实例引用
          const clipboard = new ClipboardJS(".copy-code");
          
          // 复制成功回调
          clipboard.on('success', (e) => {
            that.$message({
              message: '代码已复制到剪贴板',
              type: 'success',
              duration: 2000
            });
          });
          
          // 复制失败回调
          clipboard.on('error', (e) => {
            that.$message({
              message: '复制失败，请手动复制',
              type: 'error',
              duration: 2000
            });
          });
        }

        // 处理表格样式
        const tables = entryContent.querySelectorAll('table');
        tables.forEach(table => {
          // 避免重复包装
          if (!table.parentElement.classList.contains('table-wrapper')) {
            const wrapper = document.createElement('div');
            wrapper.className = 'table-wrapper';
            // 安全地插入wrapper和移动table
            if (table.parentNode && wrapper && wrapper.nodeType === Node.ELEMENT_NODE) {
              try {
                table.parentNode.insertBefore(wrapper, table);
                if (typeof wrapper.appendChild === 'function') {
                  wrapper.appendChild(table);
                }
              } catch (e) {
              }
            }
          }
        });
      },
      
      /**
       * 使用CSS计数器添加行号
       */
      addLineNumbersWithCSS(codeBlock) {
        if (!codeBlock) return;
        
        // 检查是否已经处理过
        if (codeBlock.classList.contains('css-line-numbers')) {
          return;
        }
        
        try {
          // 标记已处理
          codeBlock.classList.add('css-line-numbers');
          
          // 获取代码内容
          const codeContent = codeBlock.innerHTML;
          
          // 按行分割（保留HTML标签）
          let lines = codeContent.split('\n');
          
          // 移除末尾的空行
          if (lines.length > 0 && lines[lines.length - 1].trim() === '') {
            lines.pop();
          }
          
          // 创建包裹每一行的HTML
          const linesHTML = lines.map(line => {
            // 如果是空行，用一个空格占位以保持高度
            const content = line.trim() === '' ? '&nbsp;' : line;
            return `<div class="code-line">${content}</div>`;
          }).join('');
          
          // 替换内容
          codeBlock.innerHTML = linesHTML;
          
          // 根据总行数动态调整行号宽度
          const totalLines = lines.length;
          let lineNumberWidth = '15px'; // 默认宽度（1-9行）
          
          if (totalLines >= 10000) {
            lineNumberWidth = '40px';
          } else if (totalLines >= 1000) {
            lineNumberWidth = '30px';
          } else if (totalLines >= 100) {
            lineNumberWidth = '20px';
          } else if (totalLines >= 10) {
            lineNumberWidth = '15px';
          }
          
          // 设置CSS变量
          codeBlock.style.setProperty('--line-number-width', lineNumberWidth);
        } catch (e) {
        }
      },
      
      // 给代码块添加 loading 占位符
      addLoadingPlaceholders() {
        const entryContent = document.querySelector('.entry-content');
        if (!entryContent) return;
        
        // 为 Mermaid 代码块添加 loading
        const mermaidBlocks = entryContent.querySelectorAll('pre code.language-mermaid');
        mermaidBlocks.forEach(codeBlock => {
          const pre = codeBlock.parentElement;
          if (!pre.classList.contains('chart-loading')) {
            pre.classList.add('chart-loading');
            pre.setAttribute('data-chart-type', 'Mermaid');
          }
        });
        
        // 为 ECharts 代码块添加 loading
        const echartsBlocks = entryContent.querySelectorAll('pre code.language-echarts');
        echartsBlocks.forEach(codeBlock => {
          const pre = codeBlock.parentElement;
          if (!pre.classList.contains('chart-loading')) {
            pre.classList.add('chart-loading');
            pre.setAttribute('data-chart-type', 'ECharts');
          }
        });
        
        // 为普通代码块添加 loading（等待高亮）
        const codeBlocks = entryContent.querySelectorAll('pre code');
        codeBlocks.forEach(codeBlock => {
          const pre = codeBlock.parentElement;
          const classes = codeBlock.className || '';
          // 跳过 mermaid 和 echarts
          if (!classes.includes('language-mermaid') && 
              !classes.includes('language-echarts') &&
              !pre.classList.contains('highlight-wrap') &&
              !pre.classList.contains('code-loading')) {
            pre.classList.add('code-loading');
          }
        });
      },
      
      // 检测文章内容中需要加载的资源（异步并行，不阻塞渲染）
      // 注意：此方法应在 $nextTick 中调用，确保 DOM 已渲染
      detectAndLoadResources() {
        const content = this.article?.articleContent || '';
        const loadTasks = [];
        
        // 保存当前加载的文章ID（使用loadingArticleId而不是this.id，因为路由切换时this.id会先更新）
        const articleId = this.loadingArticleId;
        
        
        // 立即添加 loading 占位符（同步，因为此方法已在 $nextTick 中）
        this.addLoadingPlaceholders();
        
        // 检测是否包含代码块（需要代码高亮 + 复制功能）
        if (content.includes('```') && !isHighlightJsLoaded()) {
          const highlightTask = loadHighlightResources().then(() => {
            // 检查文章是否已切换
            if (this.loadingArticleId !== articleId) {
              return;
            }
            // 资源加载是异步的，这里需要 $nextTick
            this.$nextTick(() => {
              this.highlight();
            });
          });
          loadTasks.push(highlightTask);
        } else if (content.includes('```')) {
          // 如果已加载，立即高亮（同步，因为外层已在 $nextTick）
          this.highlight();
        }
        
        // 检测代码块时同时加载 Clipboard（代码复制功能）
        if (content.includes('```') && !isClipboardLoaded()) {
          loadClipboardResources(); // 不阻塞，后台加载即可
        }
        
        // 检测是否包含数学公式（$...$ 或 $$...$$）
        if ((content.includes('$') || content.includes('$$')) && !isKatexLoadedGlobal()) {
          loadKatexResources(); // 不阻塞，后台加载即可
        }
        
        // 检测是否包含 Mermaid 图表
        if (content.includes('```mermaid') && !isMermaidLoaded()) {
          const mermaidTask = loadMermaidResources().then(() => {
            // 检查文章是否已切换
            if (this.loadingArticleId !== articleId) {
              return;
            }
            this.$nextTick(() => {
              this.renderMermaid();
            });
          });
          loadTasks.push(mermaidTask);
        } else if (content.includes('```mermaid')) {
          // 如果已加载，立即渲染（同步）
          this.renderMermaid();
        }
        
        // 检测是否包含 ECharts 图表
        if (content.includes('```echarts') && !isEChartsLoaded()) {
          const echartsTask = loadEChartsResources().then(() => {
            // 检查文章是否已切换
            if (this.loadingArticleId !== articleId) {
              return;
            }
            this.$nextTick(() => {
              this.renderECharts();
            });
          });
          loadTasks.push(echartsTask);
        } else if (content.includes('```echarts')) {
          // 如果已加载，立即渲染（同步）
          this.renderECharts();
        }
        
        // 定义刷新目录的函数
        const refreshToc = () => {
          // 检查文章是否已切换（防止旧文章的回调影响新文章）
          if (this.loadingArticleId !== articleId) {
            return;
          }
          
          
          // 使用MutationObserver监听DOM变化，当变化停止时初始化目录
          const waitForDOMStable = (callback) => {
            this.$nextTick(() => {
              if (this.loadingArticleId !== articleId) {
                return;
              }
              
              this.addId();
              
              const entryContent = document.querySelector('.entry-content');
              if (!entryContent) {
                setTimeout(() => waitForDOMStable(callback), 50);
                return;
              }
              
              let mutationTimer = null;
              let observer = null;
              let isCallbackCalled = false;
              
              const callCallback = () => {
                if (isCallbackCalled) return;
                isCallbackCalled = true;
                
                if (observer) {
                  observer.disconnect();
                  observer = null;
                }
                if (mutationTimer) {
                  clearTimeout(mutationTimer);
                  mutationTimer = null;
                }
                
                // 使用RAF确保在浏览器渲染完成后执行
                requestAnimationFrame(() => {
                  requestAnimationFrame(() => {
                    if (this.loadingArticleId !== articleId) {
                      return;
                    }
                    callback();
                  });
                });
              };
              
              // 监听DOM变化
              observer = new MutationObserver(() => {
                // 每次DOM变化都重置定时器
                if (mutationTimer) {
                  clearTimeout(mutationTimer);
                }
                // 如果100ms内没有新的变化，认为DOM已稳定
                mutationTimer = setTimeout(() => {
                  callCallback();
                }, 100);
              });
              
              // 开始监听
              observer.observe(entryContent, {
                childList: true,      // 监听子节点变化
                subtree: true,        // 监听所有后代节点
                attributes: true,     // 监听属性变化
                attributeFilter: ['class', 'style'] // 只监听class和style变化
              });
              
              // 触发第一次检测（如果已经没有变化）
              mutationTimer = setTimeout(() => {
                callCallback();
              }, 100);
              
              // 设置最大等待时间（防止一直等待）
              setTimeout(() => {
                if (!isCallbackCalled) {
                  callCallback();
                }
              }, 1000);
            });
          };
          
          // 给highlight一些基础时间后开始监听
          setTimeout(() => {
            if (this.loadingArticleId !== articleId) {
              return;
            }
            waitForDOMStable(() => this.getTocbot());
          }, 50);
        };
        
        // 资源加载完成后的回调
        if (loadTasks.length > 0) {
          // 有资源需要异步加载
          Promise.all(loadTasks).then(() => {
            refreshToc();
          });
        } else {
          // 资源已全部加载，同步处理完成后也需要刷新目录
          refreshToc();
        }
      },
      
      async renderMermaid() {
        // 如果 Mermaid 未加载，静默返回（等待按需加载完成后再调用）
        if (!isMermaidLoaded()) {
          return;
        }
        
        const entryContent = document.querySelector('.entry-content');
        if (!entryContent) return;

        // 查找所有mermaid代码块
        const mermaidBlocks = entryContent.querySelectorAll('pre code.language-mermaid');
        
        if (mermaidBlocks.length === 0) return;
        

        try {
          for (let i = 0; i < mermaidBlocks.length; i++) {
            const codeBlock = mermaidBlocks[i];
            const pre = codeBlock.parentElement;
            
            // 跳过已经渲染过的
            if (pre.classList.contains('mermaid-rendered')) {
              continue;
            }
            
            const code = codeBlock.textContent;
            const id = `mermaid-${Date.now()}-${i}`;
            
            // 检查父节点是否存在
            if (!pre.parentNode) {
              continue;
            }
            
            // 创建容器
            const container = document.createElement('div');
            container.className = 'mermaid-container';
            
            // 保存原始代码到容器的 data 属性，以便主题切换时重新渲染
            container.setAttribute('data-mermaid-code', code);
            
            // 渲染图表
            const { svg } = await window.mermaid.render(id, code);
            container.innerHTML = svg;
            
            // 修正深色模式下的背景色（容器 + SVG）
            this.applyMermaidThemeStyles(container);
            
            // 创建放大/缩小按钮
            const zoomButton = document.createElement('button');
            zoomButton.className = 'mermaid-zoom-btn';
            zoomButton.setAttribute('aria-label', '放大图表');
            zoomButton.innerHTML = `
              <svg class="zoom-icon zoom-in-icon" viewBox="0 0 1024 1024" width="20" height="20">
                <path d="M840.824471 180.766118l-178.115765 22.106353a7.469176 7.469176 0 0 0-4.397177 12.709647l51.501177 51.501176-144.504471 144.444235a7.529412 7.529412 0 0 0 0 10.661647l42.465883 42.465883a7.529412 7.529412 0 0 0 10.661647 0l144.564706-144.564706 51.440941 51.440941c4.457412 4.457412 11.986824 1.807059 12.709647-4.397176l22.046117-177.995294a7.408941 7.408941 0 0 0-8.432941-8.372706z m-412.611765 378.578823a7.529412 7.529412 0 0 0-10.661647 0l-144.444235 144.564706-51.501177-51.501176a7.469176 7.469176 0 0 0-12.649412 4.397176L186.729412 834.861176a7.529412 7.529412 0 0 0 8.372706 8.372706l178.055529-22.106353a7.469176 7.469176 0 0 0 4.457412-12.709647l-51.501177-51.501176 144.564706-144.564706a7.529412 7.529412 0 0 0 0-10.601412l-42.526117-42.345412z" fill="currentColor"></path>
              </svg>
              <svg class="zoom-icon zoom-out-icon" style="display: none;" viewBox="0 0 1024 1024" width="20" height="20">
                <path d="M851.2 214.186667l-41.386667-41.386667a7.381333 7.381333 0 0 0-10.368 0L654.933333 317.397333l-50.176-50.176a7.253333 7.253333 0 0 0-12.373333 4.266667l-21.589333 173.525333a7.338667 7.338667 0 0 0 8.192 8.149334l173.568-21.546667c6.058667-0.725333 8.533333-8.106667 4.309333-12.373333L706.688 369.066667l144.597333-144.64a7.338667 7.338667 0 0 0-0.085333-10.24z m-406.186667 356.608l-173.568 21.589333a7.338667 7.338667 0 0 0-4.309333 12.373333l50.176 50.176-144.512 144.512a7.381333 7.381333 0 0 0 0 10.368l41.386667 41.386667a7.381333 7.381333 0 0 0 10.368 0l144.597333-144.64 50.176 50.218667a7.253333 7.253333 0 0 0 12.373333-4.309334l21.461334-173.482666a7.253333 7.253333 0 0 0-8.106667-8.192z" fill="currentColor"></path>
              </svg>
            `;
            
            // 添加点击事件处理
            zoomButton.addEventListener('click', (e) => {
              e.stopPropagation();
              this.toggleMermaidZoom(container, zoomButton);
            });
            
            // 应用深色模式按钮样式
            this.applyZoomButtonTheme(zoomButton);
            
            // 将按钮添加到容器
            container.appendChild(zoomButton);
            
            // 移除 loading 状态
            pre.classList.remove('chart-loading');
            
            // 替换代码块
            pre.parentNode.replaceChild(container, pre);
          }
        } catch (error) {
          console.error('Mermaid渲染失败:', error);
        }
      },
      
      // 渲染 ECharts 图表
      async renderECharts() {
        // 防止重复执行
        if (this._isRenderingECharts) {
          return;
        }
        
        const entryContent = document.querySelector('.entry-content');
        if (!entryContent) return;

        // 查找所有 echarts 代码块
        const echartsBlocks = entryContent.querySelectorAll('pre code.language-echarts');
        
        if (echartsBlocks.length === 0) return;
        
        // 如果 ECharts 未加载，先加载
        if (!isEChartsLoaded()) {
          await loadEChartsResources();
        }
        
        // 确保加载成功
        if (!window.echarts) {
          return;
        }
        

        this._isRenderingECharts = true;
        
        try {
          for (let i = 0; i < echartsBlocks.length; i++) {
            const codeBlock = echartsBlocks[i];
            const pre = codeBlock.parentElement;
            
            // 跳过已经渲染过的
            if (pre.classList.contains('echarts-rendered')) {
              continue;
            }
            
            try {
              // 解析 JSON 配置
              const code = codeBlock.textContent;
              const config = JSON.parse(code);
              
              // 检查父节点是否存在
              if (!pre.parentNode) {
                continue;
              }
              
              // 标记为已渲染（在替换前标记，避免重复处理）
              pre.classList.add('echarts-rendered');
              
              // 创建容器
              const container = document.createElement('div');
              container.className = 'echarts-container';
              container.style.width = '100%';
              container.style.height = config.height || '400px';
              container.style.marginBottom = '20px';
              
              // 保存原始配置到容器的 data 属性，以便主题切换时重新渲染
              container.setAttribute('data-echarts-config', code);
              
              // 移除 loading 状态
              pre.classList.remove('chart-loading');
              
              // 替换代码块
              pre.parentNode.replaceChild(container, pre);
              
              // 延迟初始化，确保DOM已渲染
              await this.$nextTick();
              
              // 检测当前主题
              const isDark = document.documentElement.classList.contains('dark-mode') || 
                           document.body.classList.contains('dark-mode');
              
              // 初始化图表（传入主题）
              const chart = window.echarts.init(container, isDark ? 'dark' : 'light');
              
              // 设置配置（自动启用动画 + 透明背景）
              const finalConfig = {
                animation: true,                    // 启用动画
                animationDuration: 1000,           // 动画时长
                animationEasing: 'cubicOut',       // 缓动效果
                animationDelay: 0,                 // 动画延迟
                backgroundColor: 'transparent',     // 透明背景，融入页面
                ...config                           // 用户配置（可覆盖默认值）
              };
              
              chart.setOption(finalConfig);
              
              // 保存 chart 实例到容器，方便后续操作
              container._echartsInstance = chart;
              
              // 响应式调整
              const resizeHandler = () => {
                if (chart && !chart.isDisposed()) {
                  chart.resize();
                }
              };
              window.addEventListener('resize', resizeHandler);
              
              // 保存 resize 处理器，便于清理
              container._resizeHandler = resizeHandler;
              
              
            } catch (parseError) {
              console.error('ECharts 配置解析失败:', parseError);
              // 解析失败时不做任何操作
            }
          }
        } catch (error) {
          console.error('ECharts渲染失败:', error);
        } finally {
          this._isRenderingECharts = false;
        }
      },
      
      // 处理主题切换事件
      async handleThemeChange(themeData) {
        
        try {
          // 查找所有已渲染的Mermaid容器
          const mermaidContainers = document.querySelectorAll('.mermaid-container');
          
          if (mermaidContainers.length === 0) {
          } else {
          
            // 遍历每个容器，重新渲染
            for (let i = 0; i < mermaidContainers.length; i++) {
              const container = mermaidContainers[i];
              
              // 从 data 属性中获取原始代码
              const originalCode = container.getAttribute('data-mermaid-code');
              if (!originalCode) {
                continue;
              }
              
              // 生成新的ID
              const newId = `mermaid-theme-${Date.now()}-${i}`;
              
              // 重新渲染图表（使用新主题）
              const { svg } = await window.mermaid.render(newId, originalCode);
              
              // 保存放大按钮（如果存在）
              const zoomButton = container.querySelector('.mermaid-zoom-btn');
              
              // 更新容器内容
              container.innerHTML = svg;
              
              // 重新添加放大按钮
              if (zoomButton) {
                container.appendChild(zoomButton);
                // 更新按钮的主题样式
                this.applyZoomButtonTheme(zoomButton);
              } else {
                // 如果没有按钮，创建一个新的
                const newZoomButton = document.createElement('button');
                newZoomButton.className = 'mermaid-zoom-btn';
                newZoomButton.setAttribute('aria-label', '放大图表');
                newZoomButton.innerHTML = `
                  <svg class="zoom-icon zoom-in-icon" viewBox="0 0 1024 1024" width="20" height="20">
                    <path d="M840.824471 180.766118l-178.115765 22.106353a7.469176 7.469176 0 0 0-4.397177 12.709647l51.501177 51.501176-144.504471 144.444235a7.529412 7.529412 0 0 0 0 10.661647l42.465883 42.465883a7.529412 7.529412 0 0 0 10.661647 0l144.564706-144.564706 51.440941 51.440941c4.457412 4.457412 11.986824 1.807059 12.709647-4.397176l22.046117-177.995294a7.408941 7.408941 0 0 0-8.432941-8.372706z m-412.611765 378.578823a7.529412 7.529412 0 0 0-10.661647 0l-144.444235 144.564706-51.501177-51.501176a7.469176 7.469176 0 0 0-12.649412 4.397176L186.729412 834.861176a7.529412 7.529412 0 0 0 8.372706 8.372706l178.055529-22.106353a7.469176 7.469176 0 0 0 4.457412-12.709647l-51.501177-51.501176 144.564706-144.564706a7.529412 7.529412 0 0 0 0-10.601412l-42.526117-42.345412z" fill="currentColor"></path>
                  </svg>
                  <svg class="zoom-icon zoom-out-icon" style="display: none;" viewBox="0 0 1024 1024" width="20" height="20">
                    <path d="M851.2 214.186667l-41.386667-41.386667a7.381333 7.381333 0 0 0-10.368 0L654.933333 317.397333l-50.176-50.176a7.253333 7.253333 0 0 0-12.373333 4.266667l-21.589333 173.525333a7.338667 7.338667 0 0 0 8.192 8.149334l173.568-21.546667c6.058667-0.725333 8.533333-8.106667 4.309333-12.373333L706.688 369.066667l144.597333-144.64a7.338667 7.338667 0 0 0-0.085333-10.24z m-406.186667 356.608l-173.568 21.589333a7.338667 7.338667 0 0 0-4.309333 12.373333l50.176 50.176-144.512 144.512a7.381333 7.381333 0 0 0 0 10.368l41.386667 41.386667a7.381333 7.381333 0 0 0 10.368 0l144.597333-144.64 50.176 50.218667a7.253333 7.253333 0 0 0 12.373333-4.309334l21.461334-173.482666a7.253333 7.253333 0 0 0-8.106667-8.192z" fill="currentColor"></path>
                  </svg>
                `;
                
                // 添加点击事件
                newZoomButton.addEventListener('click', (e) => {
                  e.stopPropagation();
                  this.toggleMermaidZoom(container, newZoomButton);
                });
                
                // 应用深色模式按钮样式
                this.applyZoomButtonTheme(newZoomButton);
                
                container.appendChild(newZoomButton);
              }
              
              // 修正深色模式下的背景色（容器 + SVG）
              this.applyMermaidThemeStyles(container);
            }
            
          }
        } catch (error) {
          console.error('主题切换时重新渲染Mermaid失败:', error);
        }
        
        // 处理 ECharts 图表主题切换
        try {
          const echartsContainers = document.querySelectorAll('.echarts-container');
          
          if (echartsContainers.length === 0) {
            return;
          }
          
          
          const isDark = (themeData && themeData.theme === 'dark') || document.body.classList.contains('dark-mode');
          
          for (let i = 0; i < echartsContainers.length; i++) {
            const container = echartsContainers[i];
            const chart = container._echartsInstance;
            
            if (!chart) {
              continue;
            }
            
            // 获取原始配置
            const configStr = container.getAttribute('data-echarts-config');
            if (!configStr) {
              continue;
            }
            
            try {
              const config = JSON.parse(configStr);
              
              // 销毁旧实例
              chart.dispose();
              
              // 使用新主题重新初始化
              const newChart = window.echarts.init(container, isDark ? 'dark' : 'light');
              
              // 重新设置配置（透明背景）
              const finalConfig = {
                animation: true,
                animationDuration: 1000,
                animationEasing: 'cubicOut',
                animationDelay: 0,
                backgroundColor: 'transparent',  // 透明背景
                ...config
              };
              
              newChart.setOption(finalConfig);
              
              // 更新实例引用
              container._echartsInstance = newChart;
              
              // 重新绑定 resize 事件
              if (container._resizeHandler) {
                window.removeEventListener('resize', container._resizeHandler);
              }
              const resizeHandler = () => newChart.resize();
              window.addEventListener('resize', resizeHandler);
              container._resizeHandler = resizeHandler;
              
            } catch (parseError) {
              console.error('ECharts 配置解析失败:', parseError);
            }
          }
          
        } catch (error) {
          console.error('主题切换时重新渲染ECharts失败:', error);
        }
      },
      
      // 应用放大按钮主题样式
      applyZoomButtonTheme(button) {
        if (!button) return;
        
        const isDark = document.body.classList.contains('dark-mode');
        
        if (isDark) {
          // 深色模式样式
          button.style.background = 'rgba(55, 55, 55, 0.95)';
          button.style.borderColor = '#555';
          button.style.boxShadow = '0 2px 8px rgba(0, 0, 0, 0.3)';
          
          // 设置图标颜色
          const icons = button.querySelectorAll('.zoom-icon');
          icons.forEach(icon => {
            icon.style.color = '#e0e0e0';
          });
        } else {
          // 浅色模式样式（清除自定义样式，使用 CSS）
          button.style.background = '';
          button.style.borderColor = '';
          button.style.boxShadow = '';
          
          const icons = button.querySelectorAll('.zoom-icon');
          icons.forEach(icon => {
            icon.style.color = '';
          });
        }
      },
      
      // 应用 Mermaid 主题样式（容器 + SVG 背景）
      applyMermaidThemeStyles(container) {
        try {
          const svg = container.querySelector('svg');
          if (!svg) return;
          
          // 检查是否为深色模式
          const isDark = document.body.classList.contains('dark-mode');
          
          // 1. 设置容器背景色（直接用 JavaScript，避免 CSS 优先级问题）
          if (isDark) {
            container.style.backgroundColor = '#2d2d2d';
          } else {
            container.style.backgroundColor = '#f8f9fa';
          }
          
          // 2. 修改 SVG 内部的背景
          if (isDark) {
            // 深色模式：查找并修改 SVG 内部的背景矩形
            const backgrounds = svg.querySelectorAll('rect[fill="#f8f9fa"], rect[fill="#F8F9FA"], rect[fill="rgb(248, 249, 250)"], rect.background, g.background rect');
            backgrounds.forEach(rect => {
              rect.setAttribute('fill', '#2d2d2d');
            });
            
            // 如果没有找到背景矩形，检查 SVG 的 style 属性
            if (svg.style.backgroundColor && svg.style.backgroundColor !== 'transparent') {
              svg.style.backgroundColor = '#2d2d2d';
            }
            
            // 兜底：直接设置 SVG 的背景色
            if (!backgrounds.length && !svg.style.backgroundColor) {
              svg.style.backgroundColor = '#2d2d2d';
            }
          } else {
            // 浅色模式：恢复默认背景
            const backgrounds = svg.querySelectorAll('rect[fill="#2d2d2d"], rect.background, g.background rect');
            backgrounds.forEach(rect => {
              rect.setAttribute('fill', '#f8f9fa');
            });
            
            if (svg.style.backgroundColor) {
              svg.style.backgroundColor = '';
            }
          }
          
        } catch (error) {
          console.error('应用Mermaid主题样式失败:', error);
        }
      },

      // 切换Mermaid图表的放大/缩小状态
      toggleMermaidZoom(container, button) {
        // 检查是否已经有放大层
        let overlay = document.getElementById('mermaid-zoom-overlay');
        
        if (overlay) {
          // 关闭放大视图
          overlay.style.transition = 'opacity 0.3s ease';
          overlay.style.opacity = '0';
          setTimeout(() => {
            if (overlay && overlay.parentNode) {
              overlay.parentNode.removeChild(overlay);
            }
          }, 300);
          document.body.style.overflow = '';
          return;
        }
        
        // 创建放大层
        overlay = document.createElement('div');
        overlay.id = 'mermaid-zoom-overlay';
        overlay.className = 'mermaid-zoom-overlay';
        
        // 获取SVG内容
        const svg = container.querySelector('svg');
        if (!svg) return;
        
        // 创建内容容器
        const content = document.createElement('div');
        content.className = 'mermaid-zoom-content';
        
        // 直接复制HTML内容
        content.innerHTML = svg.outerHTML;
        
        // 获取插入的SVG元素
        const insertedSvg = content.querySelector('svg');
        if (insertedSvg) {
          // 保留viewBox，但设置合适的宽高
          const viewBox = insertedSvg.getAttribute('viewBox');
          
          // 移除限制宽度的内联样式
          insertedSvg.removeAttribute('style');
          
          // 从viewBox计算宽高比
          if (viewBox) {
            const [x, y, width, height] = viewBox.split(' ').map(Number);
            const aspectRatio = width / height;
            
            // 设置宽度，高度自动计算
            insertedSvg.setAttribute('width', '800');
            insertedSvg.setAttribute('height', `${800 / aspectRatio}`);
          } else {
            // 如果没有viewBox，使用固定尺寸
            insertedSvg.setAttribute('width', '800');
            insertedSvg.setAttribute('height', '600');
          }
          
          // 设置样式
          insertedSvg.style.display = 'block';
          insertedSvg.style.maxWidth = '100%';
          insertedSvg.style.maxHeight = '100%';
          insertedSvg.style.width = 'auto';
          insertedSvg.style.height = 'auto';
          insertedSvg.style.margin = '0 auto';
        }
        
        // 创建关闭按钮
        const closeBtn = document.createElement('button');
        closeBtn.className = 'mermaid-zoom-close';
        closeBtn.setAttribute('aria-label', '关闭');
        closeBtn.innerHTML = `
          <svg viewBox="0 0 1024 1024" width="24" height="24">
            <path d="M557.312 513.248l265.28-263.904c12.544-12.48 12.608-32.704 0.128-45.248-12.512-12.576-32.704-12.608-45.248-0.128L512.128 467.904 246.72 204.096c-12.48-12.544-32.704-12.608-45.248-0.128-12.576 12.512-12.608 32.704-0.128 45.248l265.344 263.84-265.28 263.872c-12.544 12.48-12.608 32.704-0.128 45.248 6.24 6.272 14.464 9.44 22.688 9.44 8.16 0 16.32-3.104 22.56-9.312l265.344-263.872 265.376 263.904c6.272 6.272 14.464 9.408 22.688 9.408 8.16 0 16.32-3.104 22.56-9.312 12.544-12.48 12.608-32.704 0.128-45.248L557.312 513.248z" fill="currentColor"></path>
          </svg>
        `;
        
        // 添加元素到overlay
        overlay.appendChild(content);
        overlay.appendChild(closeBtn);
        
        // 添加到body
        document.body.appendChild(overlay);
        
        // 淡入效果
        overlay.style.opacity = '0';
        setTimeout(() => {
          overlay.style.opacity = '1';
        }, 10);
        
        // 禁止body滚动
        document.body.style.overflow = 'hidden';
        
        // 点击overlay背景或关闭按钮关闭
        const closeOverlay = () => {
          overlay.style.transition = 'opacity 0.3s ease';
          overlay.style.opacity = '0';
          setTimeout(() => {
            if (overlay && overlay.parentNode) {
              overlay.parentNode.removeChild(overlay);
            }
          }, 300);
          document.body.style.overflow = '';
        };
        
        overlay.addEventListener('click', (e) => {
          if (e.target === overlay) {
            closeOverlay();
          }
        });
        
        closeBtn.addEventListener('click', closeOverlay);
      },

      // 设置语言切换按钮的事件委托
      setupLanguageSwitchEventDelegation() {
        // 移除可能存在的旧事件监听器
        if (this.languageSwitchHandler) {
          document.removeEventListener('click', this.languageSwitchHandler, true);
          document.removeEventListener('touchend', this.languageSwitchHandler, true);
          document.removeEventListener('mousedown', this.languageSwitchHandler, true);
          document.removeEventListener('touchstart', this.languageSwitchHandler, true);
        }

        // 创建事件处理器
        this.languageSwitchHandler = (event) => {
          // 查找最近的语言切换按钮
          const button = event.target.closest('.article-language-switch .el-button[data-lang]');
          if (button && !button.disabled) {
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();

            const langCode = button.getAttribute('data-lang');
            if (langCode) {
              this.handleLanguageSwitch(langCode);
            }
            return false;
          }
        };

        // 使用捕获阶段监听多种事件类型
        document.addEventListener('click', this.languageSwitchHandler, true);
        document.addEventListener('touchend', this.languageSwitchHandler, true);
        document.addEventListener('mousedown', this.languageSwitchHandler, true);
        document.addEventListener('touchstart', this.languageSwitchHandler, true);

        // 添加直接的DOM事件监听器
        this.$nextTick(() => {
          const buttons = document.querySelectorAll('.article-language-switch .el-button[data-lang]');
          buttons.forEach(button => {
            button.addEventListener('click', (e) => {
              e.preventDefault();
              e.stopPropagation();
              const langCode = button.getAttribute('data-lang');
              if (langCode) {
                this.handleLanguageSwitch(langCode);
              }
            }, true);
          });
        });
      },

      // 原生事件处理方法
      handleMouseDown(event) {
        event.preventDefault();
        event.stopPropagation();
        const langCode = event.target.closest('[data-lang]')?.getAttribute('data-lang');
        if (langCode) {
          this.handleLanguageSwitch(langCode);
        }
      },

      handleTouchStart(event) {
        event.preventDefault();
        event.stopPropagation();
        const langCode = event.target.closest('[data-lang]')?.getAttribute('data-lang');
        if (langCode) {
          this.handleLanguageSwitch(langCode);
        }
      },

      async handleLanguageSwitch(lang) {
        // 防止重复点击
        if (lang === this.currentLang) {
          return;
        }

        // 验证语言是否在可用列表中
        const isLanguageAvailable = this.availableLanguageButtons.some(btn => btn.code === lang);
        if (!isLanguageAvailable) {
          this.$message.warning('该语言版本暂不可用');
          return;
        }
        
        // 直接调用switchLanguage，不需要try-catch
        // 因为switchLanguage内部和fetchTranslation都有完善的错误处理
        await this.switchLanguage(lang);
      },

      async switchLanguage(lang) {
        if (lang === this.currentLang) return;

        // 验证语言是否可用
        const isLanguageAvailable = this.availableLanguageButtons.some(btn => btn.code === lang);
        if (!isLanguageAvailable) {
          this.$message.warning('该语言版本暂不可用');
          return;
        }

        this.currentLang = lang;
        this.tocbotRefreshed = false; // 重置tocbot刷新标志

        // 将语言偏好与文章ID绑定，避免跨文章的语言记忆问题
        const articleLangKey = `article_${this.id}_preferredLanguage`;
        if (lang !== this.sourceLanguage) {
          localStorage.setItem(articleLangKey, lang);
        } else {
          // 如果切换回源语言，清除该文章的语言偏好
          localStorage.removeItem(articleLangKey);
        }
        
        // 同时清除全局语言偏好，确保不影响其他文章
        localStorage.removeItem('preferredLanguage');

        // 更新URL参数，不刷新页面
        this.updateUrlWithLanguage(lang);

        // 设置HTML元素的lang属性
        document.documentElement.setAttribute('lang', lang);
        
        if (lang !== this.sourceLanguage) {
          // 如果已有翻译内容，直接显示
          if (this.translatedContent) {
            // 强制更新显示翻译内容
            const md = new MarkdownIt({breaks: true})
              .use(require('markdown-it-multimd-table'))
              .use(require('@iktakahiro/markdown-it-katex'));
            this.articleContentHtml = md.render(this.translatedContent);
            this.articleContentKey = Date.now(); // 强制Vue重新渲染
            
            // 重新应用文章内容处理
            this.$nextTick(() => {
              this.$common.imgShow(".entry-content img");
              this.highlight();
              this.renderMermaid();
              this.renderECharts();
              this.addId();
              this.getTocbot();
            });
          } else {
            // 没有翻译内容，获取翻译
            await this.fetchTranslation();
          }
        } else if (lang === this.sourceLanguage) {
          // 切换到源语言，确保显示原始内容
          const md = new MarkdownIt({breaks: true}).use(require('markdown-it-multimd-table'));
          this.articleContentHtml = md.render(this.article.articleContent);
          this.articleContentKey = Date.now(); // 强制Vue重新渲染
          
          // 重新应用文章内容处理
          this.$nextTick(() => {
            this.$common.imgShow(".entry-content img");
            this.highlight();
            this.renderMermaid();
            this.renderECharts();
            this.addId();
            this.getTocbot();
          });
        }
      },
      async fetchTranslation() {
        if (!this.article || !this.article.id) {
          return;
        }
        
        this.isLoading = true;
        try {
          // 直接使用当前语言获取翻译
          const response = await this.$http.get(this.$constant.baseURL + "/article/getTranslation", {
            id: this.article.id,
            language: this.currentLang
          });

          if (response.code === 200 && response.data) {
            this.translatedTitle = response.data.title;
            this.translatedContent = response.data.content;

            // 更新文章内容显示
            // 使用与原文相同的渲染方法
            const md = new MarkdownIt({breaks: true})
              .use(require('markdown-it-multimd-table'))
              .use(require('@iktakahiro/markdown-it-katex'));
            this.articleContentHtml = md.render(this.translatedContent);
            this.articleContentKey = Date.now(); // 强制Vue重新渲染

            // 重新应用文章内容处理
            this.$nextTick(() => {
              this.$common.imgShow(".entry-content img");
              this.highlight();
              this.renderMermaid();
              this.renderECharts();
              this.addId();
              this.getTocbot();
            });
          } else if (response.code === 200 && response.data && response.data.status === 'not_found') {
            // 翻译不存在，自动降级到源语言
            this.currentLang = this.sourceLanguage;
            
            // 清除该文章的语言偏好，避免下次还是尝试加载不存在的翻译
            const articleLangKey = `article_${this.id}_preferredLanguage`;
            localStorage.removeItem(articleLangKey);
            
            // 更新URL为源语言
            this.updateUrlWithLanguage(this.sourceLanguage);
            
            // 显示原文
            const md = new MarkdownIt({breaks: true})
              .use(require('markdown-it-multimd-table'))
              .use(require('@iktakahiro/markdown-it-katex'));
            this.articleContentHtml = md.render(this.article.articleContent);
            this.articleContentKey = Date.now();
            
            this.$nextTick(() => {
              this.$common.imgShow(".entry-content img");
              this.highlight();
              this.renderMermaid();
              this.renderECharts();
              this.addId();
              this.getTocbot();
            });
            this.$message.info('该语言版本不存在，已切换到原文显示');
          } else {
            console.error('获取翻译失败，服务器返回:', response);
            // 获取失败时自动降级到源语言
            this.currentLang = this.sourceLanguage;
            
            // 清除该文章的语言偏好
            const articleLangKey = `article_${this.id}_preferredLanguage`;
            localStorage.removeItem(articleLangKey);
            
            // 更新URL为源语言
            this.updateUrlWithLanguage(this.sourceLanguage);
            
            // 显示原文
            const md = new MarkdownIt({breaks: true})
              .use(require('markdown-it-multimd-table'))
              .use(require('@iktakahiro/markdown-it-katex'));
            this.articleContentHtml = md.render(this.article.articleContent);
            this.articleContentKey = Date.now();
            
            this.$nextTick(() => {
              this.$common.imgShow(".entry-content img");
              this.highlight();
              this.renderMermaid();
              this.renderECharts();
              this.addId();
              this.getTocbot();
            });
            this.$message.error('翻译加载失败，已切换到原文显示');
          }
        } catch (error) {
          console.error('Translation error:', error);
          
          // 翻译请求失败时，自动降级到源语言显示原文
          this.currentLang = this.sourceLanguage;
          
          // 清除该文章的语言偏好，避免下次还是加载失败的翻译
          const articleLangKey = `article_${this.id}_preferredLanguage`;
          localStorage.removeItem(articleLangKey);
          
          // 更新URL为源语言
          this.updateUrlWithLanguage(this.sourceLanguage);
          
          // 显示原文内容
          const md = new MarkdownIt({breaks: true}).use(require('markdown-it-multimd-table'));
          this.articleContentHtml = md.render(this.article.articleContent);
          this.articleContentKey = Date.now();
          
          this.$nextTick(() => {
            this.$common.imgShow(".entry-content img");
            this.highlight();
            this.renderMermaid();
            this.renderECharts();
            this.addId();
            this.getTocbot();
          });
          
          this.$message.error('翻译加载失败，已切换到原文显示');
        } finally {
          this.isLoading = false;
        }
      },
      updateUrlWithLanguage(lang) {
        // 生成新的路径格式：/article/lang/id 或 /article/id（源语言）
        let newPath;
        
        if (lang === this.sourceLanguage) {
          // 源语言使用简洁格式：/article/id
          newPath = `/article/${this.id}`;
        } else {
          // 其他语言使用完整格式：/article/lang/id
          newPath = `/article/${lang}/${this.id}`;
        }

        // 保留查询参数（如果有的话）
        const query = { ...this.$route.query };

        // 使用Vue Router进行导航，避免页面刷新
        this.$router.replace({ 
          path: newPath, 
          query: query 
        }).catch(err => {
          if (err.name !== 'NavigationDuplicated') {
          }
        });
      },
      /**
       * 检查是否有临时保存的评论
       */
      checkTempComment() {
        const articleId = this.id;
        const tempCommentKey = `tempComment_${articleId}`;

        try {
          const savedComment = localStorage.getItem(tempCommentKey);
          if (savedComment) {
            const commentData = JSON.parse(savedComment);

            // 检查是否过期(24小时)
            const now = Date.now();
            const commentAge = now - commentData.timestamp;

            if (commentAge < 24 * 60 * 60 * 1000) {
              this.tempComment = commentData.content;

              // 延迟一点时间确保评论组件已加载
              setTimeout(() => {
                // 使用事件总线将评论内容发送到评论框组件
                this.$bus.$emit('restore-comment', this.tempComment);

                // 提示用户
                this.$message({
                  message: "已恢复您之前的评论内容",
                  type: "success"
                });

                // 滚动到评论区
                this.$nextTick(() => {
                  const commentElement = document.querySelector('.comment-head');
                  if (commentElement) {
                    commentElement.scrollIntoView({ behavior: 'smooth' });
                  }
                });

                // 清除临时评论
                localStorage.removeItem(tempCommentKey);
              }, 500);
            } else {
              // 过期则删除
              localStorage.removeItem(tempCommentKey);
            }
          }
        } catch (error) {
          console.error('恢复评论出错:', error);
          localStorage.removeItem(tempCommentKey);
        }
      },

      /**
       * 🔧 新方法：检查是否有保存的页面状态
       */
      checkPageState() {
        const articleId = this.id;
        const pageStateKey = `pageState_${articleId}`;

        try {
          const savedState = localStorage.getItem(pageStateKey);
          if (savedState) {
            const stateData = JSON.parse(savedState);

            // 检查是否过期(1小时)
            const now = Date.now();
            const stateAge = now - stateData.timestamp;

            if (stateAge < 60 * 60 * 1000) {
              // 延迟一点时间确保评论组件已加载
              setTimeout(() => {
                // 使用事件总线将状态数据发送到评论组件
                this.$bus.$emit('restore-page-state', stateData);

                // 恢复滚动位置
                if (stateData.scrollPosition) {
                  window.scrollTo({
                    top: stateData.scrollPosition,
                    behavior: 'smooth'
                  });
                }

                // 提示用户
                this.$message({
                  message: "已恢复您的操作状态",
                  type: "success"
                });

                // 清除保存的状态
                localStorage.removeItem(pageStateKey);
              }, 1000); // 延迟确保评论组件完全加载
            } else {
              // 过期则删除
              localStorage.removeItem(pageStateKey);
            }
          }
        } catch (error) {
          console.error('恢复页面状态出错:', error);
          localStorage.removeItem(pageStateKey);
        }
      },

      /**
       * 初始化语言设置
       * 修复重复调用 /api/translation/default-lang 接口的问题
       * 统一处理语言配置获取和语言设置逻辑
       */
      async initializeLanguageSettings() {
        try {
          // 先获取默认语言配置（只调用一次API）
          await this.getDefaultTargetLanguage();

          // 获取顺序：URL路径参数 > 当前文章的语言偏好 > 默认源语言
          const langParam = this.$route.params.lang; // 从路径参数获取语言
          const articleLangKey = `article_${this.id}_preferredLanguage`;
          const savedLang = localStorage.getItem(articleLangKey); // 只读取当前文章的语言偏好

          // 重置当前语言为源语言，避免使用上一篇文章的语言设置
          this.currentLang = this.sourceLanguage;

          if (langParam && this.languageMap[langParam]) {
            // URL路径参数优先，但必须是支持的语言
            this.currentLang = langParam;
          } else if (savedLang && this.languageMap[savedLang] && savedLang !== this.sourceLanguage) {
            // 只有当前文章有保存的语言偏好时才使用
            this.currentLang = savedLang;
          } else {
            // 使用默认源语言
            this.currentLang = this.sourceLanguage;
          }

          // 设置HTML元素的lang属性
          document.documentElement.setAttribute('lang', this.currentLang);

        } catch (error) {
          console.error('语言设置初始化失败:', error);
          // 设置默认值，确保页面能正常工作
          this.currentLang = 'zh';
          this.sourceLanguage = 'zh';
          this.targetLanguage = 'en';
          document.documentElement.setAttribute('lang', this.currentLang);
        }
      },

      async getDefaultTargetLanguage() {
        try {
          // 从Java后端获取默认语言配置
          const response = await this.$http.get(this.$constant.baseURL + "/webInfo/ai/config/articleAi/defaultLang");

          if (response.code === 200 && response.data) {
            // 设置默认目标语言
            this.targetLanguage = response.data.default_target_lang || 'en';
            this.targetLanguageName = this.languageMap[this.targetLanguage] || 'English';

            // 设置默认源语言
            this.sourceLanguage = response.data.default_source_lang || 'zh';
            this.sourceLanguageName = this.languageMap[this.sourceLanguage] || '中文';
          } else {
            this.targetLanguage = 'en';
            this.targetLanguageName = 'English';
            this.sourceLanguage = 'zh';
            this.sourceLanguageName = '中文';
          }
        } catch (error) {
          console.error('获取默认语言配置出错:', error);
          this.targetLanguage = 'en';
          this.targetLanguageName = 'English';
          this.sourceLanguage = 'zh';
          this.sourceLanguageName = '中文';
        }
      },

      async getArticleAvailableLanguages() {
        if (!this.article || !this.article.id) {
          return;
        }

        try {
          const response = await this.$http.get(this.$constant.baseURL + "/article/getAvailableLanguages", {
            id: this.article.id
          });

          if (response.code === 200 && response.data) {
            this.availableLanguages = response.data || [];

            // 生成动态语言按钮
            this.generateLanguageButtons();
          } else {
            this.availableLanguages = [];
            this.generateLanguageButtons();
          }
        } catch (error) {
          console.error('获取文章可用翻译语言出错:', error);
          this.availableLanguages = [];
          this.generateLanguageButtons();
        }
      },

      generateLanguageButtons() {
        this.availableLanguageButtons = [];

        // 始终添加原文语言按钮（通常是中文）
        this.availableLanguageButtons.push({
          code: this.sourceLanguage,
          name: this.sourceLanguageName
        });

        // 添加实际存在翻译的语言按钮
        if (this.availableLanguages && this.availableLanguages.length > 0) {
          this.availableLanguages.forEach(langCode => {
            // 避免重复添加源语言
            if (langCode !== this.sourceLanguage) {
              const langName = this.languageMap[langCode] || langCode;
              this.availableLanguageButtons.push({
                code: langCode,
                name: langName
              });
            }
          });
        }


        // 如果当前语言不在可用语言列表中，切换到源语言
        const currentLangAvailable = this.availableLanguageButtons.some(btn => btn.code === this.currentLang);
        if (!currentLangAvailable) {
          this.currentLang = this.sourceLanguage;
          
          // 清除该文章的语言偏好，因为保存的语言已不可用
          const articleLangKey = `article_${this.id}_preferredLanguage`;
          localStorage.removeItem(articleLangKey);
          
          this.updateUrlWithLanguage(this.sourceLanguage);
        }
      },

      // 打开卡片分享弹窗
      openShareCardDialog() {
        this.shareCardDialogVisible = true;
        
        // 性能优化：提前预加载html2canvas库，避免下载时等待
        this.preloadHtml2Canvas();
        
        // 延迟生成二维码，确保DOM已渲染
        this.$nextTick(() => {
          setTimeout(() => {
            this.generateQRCode();
          }, 300);
        });
      },
      
      // 预加载html2canvas库
      preloadHtml2Canvas() {
        if (typeof html2canvas === 'undefined' && !window.html2canvasLoading) {
          window.html2canvasLoading = true;
          const script = document.createElement('script');
          script.src = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
          script.onload = () => {
            window.html2canvasLoading = false;
          };
          script.onerror = () => {
            window.html2canvasLoading = false;
          };
          document.head.appendChild(script);
        }
      },

      // 格式化日期
      formatDate(dateStr) {
        if (!dateStr) return '';
        
        try {
          const date = new Date(dateStr);
          const year = date.getFullYear();
          const month = String(date.getMonth() + 1).padStart(2, '0');
          const day = String(date.getDate()).padStart(2, '0');
          
          return `${year}年${month}月${day}日`;
        } catch (error) {
          console.error('日期格式化失败:', error);
          return dateStr;
        }
      },

      // 生成二维码（调用后端API）
      generateQRCode() {
        const qrcodeContainer = this.$refs.qrcode;
        if (!qrcodeContainer) {
          console.error('二维码容器未找到');
          return;
        }

        // 检查文章ID是否存在
        if (!this.article || !this.article.id) {
          console.error('文章ID不存在');
          qrcodeContainer.innerHTML = '<div style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999;">无效文章</div>';
          return;
        }

        // 清空现有内容
        qrcodeContainer.innerHTML = '';

        // 显示加载中
        qrcodeContainer.innerHTML = '<div style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999;">加载中...</div>';

        // 调用后端API生成二维码
        const qrcodeApiUrl = `${this.$constant.baseURL}/qrcode/article/${this.article.id}`;

        // 创建img元素显示二维码
        const img = document.createElement('img');
        img.src = qrcodeApiUrl;
        img.style.width = '60px';
        img.style.height = '60px';
        img.style.display = 'block';
        
        img.onload = () => {
          qrcodeContainer.innerHTML = '';
          qrcodeContainer.appendChild(img);
        };

        img.onerror = () => {
          console.error('二维码加载失败');
          qrcodeContainer.innerHTML = '<div style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #999;">加载失败</div>';
        };
      },

      // 下载卡片
      downloadShareCard() {
        const shareCard = this.$refs.shareCard;
        if (!shareCard) {
          this.$message.error('卡片元素未找到');
          return;
        }

        // 性能优化：检查库是否已加载（通过预加载应该已经完成）
        if (typeof html2canvas === 'undefined') {
          if (window.html2canvasLoading) {
            // 正在加载中，等待加载完成
            this.$message({
              message: '正在加载必要组件，请稍候...',
              type: 'info',
              duration: 1500
            });
            
            const checkInterval = setInterval(() => {
              if (typeof html2canvas !== 'undefined') {
                clearInterval(checkInterval);
                this.captureAndDownloadCard(shareCard);
              }
            }, 100);
            
            // 超时保护
            setTimeout(() => {
              clearInterval(checkInterval);
              if (typeof html2canvas === 'undefined') {
                this.$message.error('组件加载超时，请刷新页面重试');
              }
            }, 10000);
          } else {
            // 未加载也未在加载中，立即加载
            this.$message({
              message: '首次使用，正在加载组件...',
              type: 'info',
              duration: 2000
            });
            
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
            script.onload = () => {
              this.captureAndDownloadCard(shareCard);
            };
            script.onerror = () => {
              this.$message.error('组件加载失败，请检查网络连接');
            };
            document.head.appendChild(script);
          }
        } else {
          // 库已加载，直接生成
          this.captureAndDownloadCard(shareCard);
        }
      },

      // 捕获并下载卡片
      captureAndDownloadCard(element) {
        // 显示加载中的消息
        const loadingMsg = this.$message({
          message: '正在生成卡片图片...',
          type: 'info',
          duration: 0,  // 不自动关闭
          showClose: false
        });

        // 性能优化：使用requestIdleCallback在空闲时渲染，避免阻塞UI
        const renderCard = () => {
          html2canvas(element, {
            useCORS: true,
            allowTaint: true,
            backgroundColor: '#F5EFE6',
            scale: 2, // 提高清晰度
            logging: false,
            // 性能优化：忽略不必要的元素
            ignoreElements: (element) => {
              return element.classList?.contains('el-loading-mask');
            },
            // 性能优化：使用更快的渲染选项
            removeContainer: true,
            imageTimeout: 5000  // 图片加载超时
          }).then(canvas => {
            loadingMsg.close();  // 关闭加载提示
          // 转换为图片并下载
          canvas.toBlob((blob) => {
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            
            // 生成文件名
            const fileName = `${this.article.articleTitle || '文章'}_分享卡片.png`;
            link.download = fileName;
            
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);

            this.$message.success('卡片已下载');
          }, 'image/png');
        }).catch(error => {
          loadingMsg.close();  // 关闭加载提示
          console.error('生成卡片失败:', error);
          this.$message.error('生成卡片失败，请重试');
        });
      };
      
      // 使用requestIdleCallback优化，如果不支持则直接执行
      if (window.requestIdleCallback) {
        requestIdleCallback(renderCard, { timeout: 1000 });
      } else {
        setTimeout(renderCard, 0);
      }
    }
    }
  }
</script>

<style scoped>

  .article-head {
    height: 40vh;
    position: relative;
    /* 确保整个头部区域在文章容器之上，避免被动画创建的层叠上下文遮挡 */
    z-index: 10;
  }

  /* 确保article-head内的所有绝对定位元素不会覆盖语言切换按钮 */
  .article-head > * {
    pointer-events: auto;
  }

  .article-head .el-image {
    pointer-events: none;
  }

  .article-head .el-image * {
    pointer-events: none;
  }

  .article-image::before {
    position: absolute;
    width: 100%;
    height: 100%;
    background-color: var(--miniMask);
    content: "";
    z-index: 1;
    pointer-events: none;
  }

  .error-text {
    font-size: 22px;
    line-height: 1.8;
    letter-spacing: 2px;
    color: var(--white);
    padding: 20px;
    text-align: center;
    word-break: break-word;
  }

  .article-info-container {
    position: absolute;
    bottom: 15px;
    left: 20%;
    color: var(--white);
    z-index: 1000;
  }



  .article-info-news {
    position: absolute;
    bottom: 10px;
    right: 20%;
    cursor: pointer;
    animation: scale 1s ease-in-out infinite;
  }

  .article-title {
    font-size: 28px;
    margin-bottom: 15px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    word-break: break-word;
  }

  .article-info {
    font-size: 14px;
    user-select: none;
  }

  .article-info i {
    margin-right: 6px;
  }

  .article-info span:not(:last-child) {
    margin-right: 5px;
  }

  .article-container {
    max-width: 800px;
    margin: 0 auto;
    padding: 40px 20px;
    /* 确保不会因为动画的层叠上下文而遮挡头部元素 */
    position: relative;
    z-index: 1;
  }

  .article-update-time {
    color: var(--greyFont);
    font-size: 12px;
    margin: 20px 0;
    user-select: none;
  }

  blockquote {
    line-height: 2;
    border-left: 0.2rem solid var(--blue);
    padding: 10px 1rem;
    background-color: var(--azure);
    border-radius: 4px;
    margin: 0 0 40px 0;
    user-select: none;
    color: var(--black);
  }

  .article-sort {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 20px;
  }

  .article-sort span {
    padding: 3px 10px;
    background-color: var(--themeBackground);
    border-radius: 5px;
    font-size: 14px;
    color: var(--white);
    /* 性能优化: 只监听实际变化的属性 */
    transition: background-color 0.3s ease, transform 0.3s ease, opacity 0.3s ease;
    margin-right: 25px;
    cursor: pointer;
    user-select: none;
    transform: translateZ(0);
  }

  .article-sort span:hover {
    background-color: var(--red);
  }

  .article-like {
    color: var(--red) !important;
  }

  .article-like-icon {
    font-size: 60px;
    cursor: pointer;
    color: var(--greyFont);
    /* 性能优化: 只监听实际变化的属性 */
    transition: color 0.5s ease, transform 0.5s ease;
    border-radius: 50%;
    margin-bottom: 20px;
    transform: translateZ(0);
  }

  .article-like-icon:hover {
    transform: rotate(360deg);
  }

  .subscribe-button {
    background: rgb(119, 48, 152);
    width: 110px;
    padding: 8px 0;
    font-size: 16px;
    text-align: center;
    color: var(--white);
    border-radius: 6px;
    cursor: pointer;
    /* 性能优化: 只监听实际变化的属性 */
    transition: background-color 0.3s ease, transform 0.3s ease, box-shadow 0.3s ease;
    user-select: none;
    transform: translateZ(0);
  }

  .subscribe-button i {
    margin-left: 0;
  }

  .subscribe-button:hover {
    background: rgb(99, 28, 132);
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(119, 48, 152, 0.3);
  }

  .subscribe-button.subscribed {
    background: rgb(76, 175, 80);
  }

  .subscribe-button.subscribed:hover {
    background: rgb(56, 155, 60);
    box-shadow: 0 4px 8px rgba(76, 175, 80, 0.3);
  }

  /* 卡片分享按钮样式 */
  .share-card-button {
    background: #ff416c;
    width: 110px;
    padding: 8px 0;
    font-size: 16px;
    text-align: center;
    color: var(--white);
    border-radius: 6px;
    cursor: pointer;
    /* 性能优化: 只监听实际变化的属性 */
    transition: background-color 0.3s ease, transform 0.3s ease, box-shadow 0.3s ease;
    user-select: none;
    margin-left: 15px;
    transform: translateZ(0);
  }

  .share-card-button i {
    margin-left: 0;
  }

  .share-card-button:hover {
    background: #e63a5f;
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(255, 65, 108, 0.3);
  }

  /* 卡片分享弹窗样式 */
  .share-card-container {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 10px;
    background: transparent;
  }

  .share-card-preview {
    background: hsla(0, 0%, 100%, .7019607843137254);
    border-radius: 12px;
    padding: 25px;
    width: 100%;
    max-width: 400px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    position: relative;
  }

  .card-avatar-container {
    display: flex;
    justify-content: flex-start;
    margin-bottom: 12px;
  }

  .card-avatar {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    object-fit: cover;
    border: 2px solid #fff;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  }

  .card-date {
    font-size: 13px;
    color: #00000091;
    margin-bottom: 12px;
    font-weight: 400;
  }

  .card-title {
    font-size: 20px;
    font-weight: bold;
    color: #333;
    margin-bottom: 18px;
    line-height: 1.4;
    word-wrap: break-word;
  }

  .card-cover {
    width: 100%;
    height: 220px;
    border-radius: 8px;
    overflow: hidden;
    margin-bottom: 18px;
  }

  .card-cover img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .card-footer {
    margin-top: 15px;
  }

  .card-author {
    text-align: right;
    font-size: 14px;
    color: #00000091;
    margin-bottom: 15px;
    font-weight: 500;
  }

  .card-divider {
    width: 100%;
    margin-top: 0;
    margin-bottom: 10px;
    border: 1px solid hsla(0, 0%, 60%, .10196078431372549);
  }

  .card-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-brand {
    font-size: 20px;
    color: #00000091;
    font-family: 'Arial', sans-serif;
    line-height: 1;
    margin: auto 0;
  }

  .card-qrcode {
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .card-qrcode img {
    width: 100%;
    height: 100%;
  }

  /* 响应式设计 - 对话框宽度由 centered-dialog.css 全局样式处理 */
  @media (max-width: 768px) {
    .share-card-preview {
      max-width: 100%;
      padding: 18px;
    }

    .card-avatar {
      width: 35px;
      height: 35px;
    }

    .card-date {
      font-size: 12px;
    }

    .card-title {
      font-size: 18px;
      margin-bottom: 15px;
    }

    .card-cover {
      height: 180px;
    }

    .card-author {
      font-size: 13px;
    }

    .card-brand {
      font-size: 18px;
      letter-spacing: 1px;
    }

    .card-qrcode {
      width: 50px;
      height: 50px;
    }

    .share-card-button {
      margin-left: 10px;
      width: 100px;
      font-size: 14px;
    }
  }


  .process-wrap {
    margin: 0 0 40px;
  }

  .process-wrap hr {
    position: relative;
    margin: 10px auto 60px;
    border: 2px dashed var(--lightGreen);
    overflow: visible;
  }

  .process-wrap hr:before {
    position: absolute;
    top: -14px;
    left: 5%;
    color: var(--lightGreen);
    content: '❄';
    font-size: 30px;
    line-height: 1;
    /* 性能优化: 有位移动画，需要GPU加速 */
    transition: transform 1s ease-in-out, left 1s ease-in-out;
    will-change: transform, left;
    transform: translateZ(0);
  }

  .process-wrap hr:hover:before {
    left: calc(95% - 20px);
  }

  .process-wrap >>> .el-collapse-item__header {
    border-bottom: unset;
    font-size: 20px;
    background-color: var(--background);
    color: var(--lightGreen);
  }

  .process-wrap >>> .el-collapse-item__wrap {
    background-color: var(--background);
  }

  .process-wrap .el-collapse {
    border-top: unset;
    border-bottom: unset;
  }

  .process-wrap >>> .el-collapse-item__wrap {
    border-bottom: unset;
  }

  .password-content {
    font-size: 13px;
    color: var(--maxGreyFont);
    line-height: 1.5;
  }



  .copyright-container {
    color: var(--black);
    line-height: 2.5;
    padding: 0 30px 10px;
    font-size: 16px;
  }

  @media screen and (max-width: 700px) {
    .article-info-container {
      left: 20px;
      max-width: 320px;
      z-index: 1000;
    }

    .article-info-news {
      right: 20px;
    }
  }

  /* 语言切换按钮容器样式 */
  .language-switch-container {
    position: relative;
    /* z-index已优化：article-head的z-index: 10已经解决了层级问题，不需要超高的z-index */
    z-index: 1;
    width: 100%;
    pointer-events: none;
    margin-bottom: 15px;
    clear: both;
    isolation: isolate;
    transform: translateZ(0);
  }

  /* 确保容器内的按钮可以接收点击事件 */
  .language-switch-container * {
    pointer-events: auto;
  }

  /* 语言切换按钮样式 */
  .article-language-switch {
    position: relative;
    z-index: 1;
    margin-top: 10px; /* 添加上边距，与封面保持距离 */
    margin-bottom: 20px;
    margin-left: 10px; /* 添加左边距 */
    pointer-events: auto;
    transform: translateZ(0);
    will-change: transform;
  }

  .article-language-switch .el-button-group {
    position: relative;
    z-index: 1;
    box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.2);
    border-radius: 6px;
    overflow: hidden;
    backdrop-filter: blur(10px);
    pointer-events: auto;
    transform: translateZ(0);
    isolation: isolate;
  }

  .article-language-switch .el-button {
    position: relative;
    z-index: 1;
    padding: 8px 15px;
    font-weight: 500;
    font-size: 13px;
    /* 性能优化: 只监听背景色和边框 */
    transition: background-color 0.3s ease, border-color 0.3s ease, color 0.3s ease;
    background: rgba(255, 255, 255, 0.9);
    border-color: rgba(255, 255, 255, 0.9);
    cursor: pointer;
    user-select: none;
    pointer-events: auto !important;
    touch-action: manipulation;
    transform: translateZ(0);
    isolation: isolate;
  }

  /* 强制确保按钮及其子元素可点击 */
  .article-language-switch .el-button,
  .article-language-switch .el-button *,
  .article-language-switch .el-button::before,
  .article-language-switch .el-button::after {
    pointer-events: auto !important;
  }

  .article-language-switch .el-button:hover {
    background: rgba(255, 255, 255, 1);
    border-color: rgba(255, 255, 255, 1);
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }

  .article-language-switch .el-button--primary {
    background-color: var(--themeBackground) !important;
    border-color: var(--themeBackground) !important;
    color: var(--white) !important;
  }

  .article-language-switch .el-button--primary:hover {
    background-color: var(--themeBackground) !important;
    border-color: var(--themeBackground) !important;
    opacity: 0.9;
  }

  .article-language-switch .el-button.is-disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }



  /* 注释原因：通过CSS层叠上下文（.article-head z-index: 10 和 .article-container z-index: 1）
     已经彻底解决了语言切换按钮被遮挡的问题，无需在中等屏幕下隐藏按钮 */
  /* 中等屏幕适配 - 隐藏原有语言切换按钮 */
  /* @media (max-width: 1050px) {
    .language-switch-container {
      display: none !important;
    }

    .article-language-switch {
      display: none !important;
    }
  } */

  /* 移动端适配 */
  @media (max-width: 768px) {
    .language-switch-container {
      position: relative;
      z-index: 1;
      margin-bottom: 10px;
      pointer-events: none;
      isolation: isolate;
      transform: translateZ(0);
    }

    .article-language-switch {
      position: relative;
      z-index: 1;
      margin-top: 15px; /* 移动端上边距稍小 */
      margin-bottom: 10px;
      margin-left: 15px; /* 移动端左边距稍小 */
      pointer-events: auto;
      transform: translateZ(0);
      will-change: transform;
    }

    .article-language-switch .el-button {
      position: relative;
      z-index: 1;
      padding: 6px 12px;
      font-size: 12px;
      min-height: 32px;
      pointer-events: auto;
      touch-action: manipulation;
      transform: translateZ(0);
      isolation: isolate;
    }

    .article-language-switch .el-button-group {
      position: relative;
      z-index: 1;
      pointer-events: auto;
      transform: translateZ(0);
      isolation: isolate;
    }
  }

  /* Mermaid图表容器 */
  ::v-deep .mermaid-container {
    position: relative;
    margin: 20px 0;
    padding: 20px;
    background: #f8f9fa;
    border-radius: 8px;
    overflow-x: auto;
    text-align: center;
    transition: all 0.3s ease;
  }
  
  /* 深色模式下的容器背景 - 提高优先级 */
  body.dark-mode ::v-deep .mermaid-container {
    background: #2d2d2d !important;
  }

  ::v-deep .mermaid-container svg {
    max-width: 100%;
    height: auto;
  }

  /* Mermaid放大/缩小按钮 */
  ::v-deep .mermaid-zoom-btn {
    position: absolute;
    top: 10px;
    right: 10px;
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.95);
    border: 1px solid #ddd;
    border-radius: 6px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s ease;
    z-index: 10;
    padding: 0;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    backdrop-filter: blur(4px);
  }

  ::v-deep .mermaid-zoom-btn:hover {
    background: rgba(255, 255, 255, 1);
    border-color: #409eff;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
    transform: scale(1.05);
  }

  ::v-deep .mermaid-zoom-btn:active {
    transform: scale(0.95);
  }

  ::v-deep .mermaid-zoom-btn .zoom-icon {
    width: 20px;
    height: 20px;
    color: #333;
    transition: color 0.2s ease;
  }

  ::v-deep .mermaid-zoom-btn:hover .zoom-icon {
    color: #409eff;
  }

  /* 深色模式下的放大按钮样式 */
  body.dark-mode ::v-deep .mermaid-zoom-btn,
  .dark-mode ::v-deep .mermaid-zoom-btn {
    background: rgba(55, 55, 55, 0.95);
    border: 1px solid #555;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  body.dark-mode ::v-deep .mermaid-zoom-btn:hover,
  .dark-mode ::v-deep .mermaid-zoom-btn:hover {
    background: rgba(70, 70, 70, 1);
    border-color: #4a9eff;
    box-shadow: 0 4px 12px rgba(74, 158, 255, 0.4);
  }
  
  body.dark-mode ::v-deep .mermaid-zoom-btn .zoom-icon,
  .dark-mode ::v-deep .mermaid-zoom-btn .zoom-icon {
    color: #e0e0e0;
  }
  
  body.dark-mode ::v-deep .mermaid-zoom-btn:hover .zoom-icon,
  .dark-mode ::v-deep .mermaid-zoom-btn:hover .zoom-icon {
    color: #4a9eff;
  }
  
  /* ========== Loading 占位符样式 ========== */
  
  /* 默认隐藏 echarts 和 mermaid 代码块内容，防止闪烁 */
  ::v-deep pre:has(code.language-echarts) code,
  ::v-deep pre:has(code.language-mermaid) code {
    opacity: 0;
    position: absolute;
  }
  
  /* 隐藏 loading 状态下的复制按钮 */
  ::v-deep pre:has(code.language-echarts) .copy-btn,
  ::v-deep pre:has(code.language-mermaid) .copy-btn {
    display: none !important;
  }
  
  /* 为 echarts 和 mermaid 代码块自动添加占位符样式 */
  ::v-deep pre:has(code.language-echarts),
  ::v-deep pre:has(code.language-mermaid) {
    position: relative;
    min-height: 400px;
    background: var(--background, #ffffff);
    border-radius: 8px;
    overflow: hidden;
    margin: 20px 0;
    padding: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }
  
  /* 添加加载动画（居中） */
  ::v-deep pre:has(code.language-echarts)::before,
  ::v-deep pre:has(code.language-mermaid)::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 40px;
    height: 40px;
    border: 4px solid #f3f3f3;
    border-top: 4px solid #409EFF;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    z-index: 1;
  }
  
  /* 图表 loading 样式（兼容旧逻辑） */
  ::v-deep pre.chart-loading {
    position: relative;
    min-height: 400px;
    background: var(--background, #ffffff);
    border-radius: 8px;
    overflow: hidden;
    margin: 20px 0;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }
  
  ::v-deep pre.chart-loading code {
    display: none !important;
  }
  
  ::v-deep pre.chart-loading::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 40px;
    height: 40px;
    border: 4px solid #f3f3f3;
    border-top: 4px solid #409EFF;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
  
  /* 代码块 loading 样式 */
  ::v-deep pre.code-loading {
    position: relative;
    min-height: 100px;
  }
  
  ::v-deep pre.code-loading code {
    opacity: 0.3;
  }
  
  /* 暗色模式下的 loading */
  body.dark-mode ::v-deep pre:has(code.language-echarts),
  body.dark-mode ::v-deep pre:has(code.language-mermaid),
  body.dark-mode ::v-deep pre.chart-loading {
    background: rgba(255, 255, 255, 0.03) !important;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.5) !important;
    border: 1px solid rgba(255, 255, 255, 0.05);
  }
  
  body.dark-mode ::v-deep pre:has(code.language-echarts)::before,
  body.dark-mode ::v-deep pre:has(code.language-mermaid)::before,
  body.dark-mode ::v-deep pre.chart-loading::before {
    border-color: rgba(255, 255, 255, 0.1);
    border-top-color: #409EFF;
  }
  
  /* Loading 旋转动画 */
  
  @keyframes spin {
    0% { transform: translateX(-50%) rotate(0deg); }
    100% { transform: translateX(-50%) rotate(360deg); }
  }
  
  /* ========== ECharts图表样式 ========== */
  
  /* ECharts图表容器 */
  ::v-deep .echarts-container {
    position: relative;
    margin: 20px 0;
    padding: 0;
    background: transparent;
    border-radius: 8px;
    overflow: visible;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    transition: box-shadow 0.3s ease, transform 0.3s ease;
  }
  
  ::v-deep .echarts-container:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
    transform: translateY(-2px);
  }
  
  /* 深色模式下的 ECharts 容器 */
  body.dark-mode ::v-deep .echarts-container {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.5);
    background: rgba(255, 255, 255, 0.03);
  }
  
  body.dark-mode ::v-deep .echarts-container:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.6);
    background: rgba(255, 255, 255, 0.05);
  }
  
  /* ECharts 图表画布 */
  ::v-deep .echarts-container canvas {
    display: block;
    border-radius: 8px;
  }
  
  /* ECharts 动画效果 */
  ::v-deep .echarts-container {
    animation: fadeInUp 0.6s ease-out;
  }
  
  @keyframes fadeInUp {
    from {
      opacity: 0;
      transform: translateY(20px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

</style>

<style>
/* 卡片分享对话框样式（非 scoped）*/
.share-card-dialog .el-dialog {
  background: #f5f4ce !important;
  border-radius: 12px !important;
}

.share-card-dialog .el-dialog__header {
  background: #f5f4ce !important;
  border-radius: 12px 12px 0 0 !important;
  padding: 20px 20px 10px !important;
}

.share-card-dialog .el-dialog__body {
  background: #f5f4ce !important;
  padding: 10px 20px !important;
}

.share-card-dialog .el-dialog__footer {
  background: #f5f4ce !important;
  border-radius: 0 0 12px 12px !important;
  padding: 10px 20px 20px !important;
}

.share-card-dialog .el-dialog__footer .el-button {
  border-radius: 25px;
  padding: 6px 20px;
  font-size: 15px;
  margin-bottom: 20px;
}

/* 文章订阅对话框垂直居中 */
.el-message-box__wrapper {
  align-items: center;
}

.el-message-box {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  margin-top: 0 !important;
}

/* 订阅对话框按钮样式 - 与卡片分享对话框保持一致 */
.el-message-box .el-button {
  border-radius: 25px;
  padding: 6px 20px;
  font-size: 15px;
}

/* ========== 暗色模式适配 - 卡片分享对话框 ========== */
body.dark-mode .share-card-dialog .el-dialog {
  background: #2c2c2c !important;
}

body.dark-mode .share-card-dialog .el-dialog__header {
  background: #2c2c2c !important;
}

body.dark-mode .share-card-dialog .el-dialog__body {
  background: #2c2c2c !important;
}

body.dark-mode .share-card-dialog .el-dialog__footer {
  background: #2c2c2c !important;
}

body.dark-mode .share-card-dialog .el-dialog__title {
  color: #e0e0e0 !important;
}

/* 暗色模式下对话框关闭按钮 */
body.dark-mode .share-card-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #b0b0b0 !important;
}

body.dark-mode .share-card-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #ffffff !important;
}

/* ========== 暗色模式适配 - 文章订阅对话框（MessageBox）========== */
body.dark-mode .el-message-box {
  background-color: #2c2c2c !important;
  border: 1px solid #404040 !important;
}

body.dark-mode .el-message-box__header {
  background-color: #2c2c2c !important;
}

body.dark-mode .el-message-box__title {
  color: #e0e0e0 !important;
}

body.dark-mode .el-message-box__content {
  color: #b0b0b0 !important;
}

body.dark-mode .el-message-box__message {
  color: #b0b0b0 !important;
}

body.dark-mode .el-message-box__headerbtn .el-message-box__close {
  color: #b0b0b0 !important;
}

body.dark-mode .el-message-box__headerbtn:hover .el-message-box__close {
  color: #ffffff !important;
}

/* 订阅对话框按钮暗色模式 */
body.dark-mode .el-message-box .el-button--default {
  background-color: #404040 !important;
  border-color: #505050 !important;
  color: #e0e0e0 !important;
}

body.dark-mode .el-message-box .el-button--default:hover {
  background-color: #505050 !important;
  border-color: #606060 !important;
}

body.dark-mode .el-message-box .el-button--primary {
  background-color: #409eff !important;
  border-color: #409eff !important;
}

body.dark-mode .el-message-box .el-button--primary:hover {
  background-color: #66b1ff !important;
  border-color: #66b1ff !important;
}

/* ========== 暗色模式适配 - 版权声明对话框 ========== */
body.dark-mode .article-copy .el-dialog {
  background-color: #2c2c2c !important;
}

body.dark-mode .article-copy .el-dialog__header {
  background-color: #2c2c2c !important;
}

body.dark-mode .article-copy .el-dialog__title {
  color: #e0e0e0 !important;
}

body.dark-mode .article-copy .el-dialog__body {
  background-color: #2c2c2c !important;
  color: #b0b0b0 !important;
}

body.dark-mode .article-copy .el-dialog__headerbtn .el-dialog__close {
  color: #b0b0b0 !important;
}

body.dark-mode .article-copy .el-dialog__headerbtn:hover .el-dialog__close {
  color: #ffffff !important;
}

/* 版权声明对话框内容适配 */
body.dark-mode .article-copy .copyright-container {
  color: #e0e0e0 !important;
}

body.dark-mode .article-copy .copyright-container p {
  color: #e0e0e0 !important;
}

body.dark-mode .article-copy .copyright-container ul {
  color: #e0e0e0 !important;
}

body.dark-mode .article-copy .copyright-container li {
  color: #e0e0e0 !important;
}

body.dark-mode .article-copy .copyright-container a {
  color: #66b1ff !important;
}

body.dark-mode .article-copy .copyright-container a:hover {
  color: #409eff !important;
}

/* ========== 暗色模式适配 - 通用 el-dialog ========== */
body.dark-mode .el-dialog {
  background-color: #2c2c2c !important;
}

body.dark-mode .el-dialog__header {
  background-color: #2c2c2c !important;
  border-bottom-color: #404040 !important;
}

body.dark-mode .el-dialog__title {
  color: #e0e0e0 !important;
}

body.dark-mode .el-dialog__body {
  background-color: #2c2c2c !important;
  color: #b0b0b0 !important;
}

body.dark-mode .el-dialog__footer {
  background-color: #2c2c2c !important;
  border-top-color: #404040 !important;
}

body.dark-mode .el-dialog__headerbtn .el-dialog__close {
  color: #b0b0b0 !important;
}

body.dark-mode .el-dialog__headerbtn:hover .el-dialog__close {
  color: #ffffff !important;
}

/* 暗色模式下对话框中的输入框 */
body.dark-mode .el-dialog .el-input__inner {
  background-color: #404040 !important;
  border-color: #505050 !important;
  color: #e0e0e0 !important;
}

body.dark-mode .el-dialog .el-input__inner::placeholder {
  color: #888888 !important;
}

body.dark-mode .el-dialog .el-input__inner:focus {
  border-color: #409eff !important;
}

/* 暗色模式下对话框中的按钮 */
body.dark-mode .el-dialog .el-button--default {
  background-color: #404040 !important;
  border-color: #505050 !important;
  color: #e0e0e0 !important;
}

body.dark-mode .el-dialog .el-button--default:hover {
  background-color: #505050 !important;
  border-color: #606060 !important;
}

body.dark-mode .el-dialog .el-button--primary {
  background-color: #409eff !important;
  border-color: #409eff !important;
}

body.dark-mode .el-dialog .el-button--primary:hover {
  background-color: #66b1ff !important;
  border-color: #66b1ff !important;
}

/* Mermaid图表放大overlay */
.mermaid-zoom-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.85);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.3s ease;
}

.mermaid-zoom-content {
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  justify-content: center;
  overflow: auto;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.mermaid-zoom-content svg {
  max-width: 100%;
  max-height: 100%;
  width: auto !important;
  height: auto !important;
  display: block !important;
  visibility: visible !important;
  opacity: 1 !important;
}

.dark-mode .mermaid-zoom-content {
  background: #2d2d2d;
}

.mermaid-zoom-close {
  position: fixed;
  top: 20px;
  right: 20px;
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #ddd;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  padding: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  z-index: 10001;
}

.mermaid-zoom-close:hover {
  background: rgba(255, 255, 255, 1);
  transform: scale(1.1) rotate(90deg);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

.mermaid-zoom-close svg {
  color: #333;
  transition: color 0.2s ease;
}

.mermaid-zoom-close:hover svg {
  color: #ff4444;
}

/* 暗色模式 */
.dark-mode .mermaid-zoom-close {
  background: rgba(45, 45, 45, 0.9);
  border-color: #555;
}

.dark-mode .mermaid-zoom-close:hover {
  background: rgba(45, 45, 45, 1);
}

.dark-mode .mermaid-zoom-close svg {
  color: #ddd;
}

.dark-mode .mermaid-zoom-close:hover svg {
  color: #ff6666;
}
</style>