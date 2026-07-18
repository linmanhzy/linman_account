package com.linman.account.config;

import com.linman.account.entity.Category;
import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import com.linman.account.repository.CategoryRepository;
import com.linman.account.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${myapp.admin.username:admin}")
    private String adminUsername;

    @Value("${myapp.admin.password:admin123456}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository, CategoryRepository categoryRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ENABLED);
            admin.setCreatedAt(LocalDateTime.now());
            userRepository.save(admin);
            log.info("已创建默认管理员账户：{}（请尽快修改默认密码）", adminUsername);
        }

        if (categoryRepository.countByUserIdIsNull() == 0) {
            seedDefaultCategories();
            log.info("已写入系统预设分类");
        }
    }

    // 系统预设分类（与前端 categories.ts 保持一致），仅首次启动写入一次
    private void seedDefaultCategories() {
        int order = 0;
        for (SeedL1 l1 : DEFAULT_EXPENSE) {
            Category parent = saveSystem(l1.name, "expense", l1.icon, order++);
            for (String child : l1.children) {
                saveSystemChild(child, "expense", parent.getId());
            }
        }
        for (SeedL1 l1 : DEFAULT_INCOME) {
            Category parent = saveSystem(l1.name, "income", l1.icon, order++);
            for (String child : l1.children) {
                saveSystemChild(child, "income", parent.getId());
            }
        }
    }

    private Category saveSystem(String name, String type, String icon, int sortOrder) {
        Category c = new Category();
        c.setName(name);
        c.setType(type);
        c.setIcon(icon);
        c.setParentId(null);
        c.setSortOrder(sortOrder);
        c.setSystem(true);
        c.setUserId(null);
        c.setCreatedAt(LocalDateTime.now());
        return categoryRepository.save(c);
    }

    private void saveSystemChild(String name, String type, Long parentId) {
        Category c = new Category();
        c.setName(name);
        c.setType(type);
        c.setParentId(parentId);
        c.setSortOrder(0);
        c.setSystem(true);
        c.setUserId(null);
        c.setCreatedAt(LocalDateTime.now());
        categoryRepository.save(c);
    }

    // ====== 预设分类数据 ======
    private static final class SeedL1 {
        final String name;
        final String icon;
        final List<String> children;

        SeedL1(String name, String icon, List<String> children) {
            this.name = name;
            this.icon = icon;
            this.children = children;
        }
    }

    private static final List<SeedL1> DEFAULT_EXPENSE = List.of(
        new SeedL1("餐饮饮食", "🍜", List.of("早餐", "午餐", "晚餐", "零食饮品", "聚餐请客")),
        new SeedL1("交通出行", "🚗", List.of("公交地铁", "打车拼车", "加油充电", "停车费", "车辆保养")),
        new SeedL1("购物消费", "🛒", List.of("服装鞋帽", "日用品", "数码电子", "家居装饰", "护肤美妆")),
        new SeedL1("居住住房", "🏠", List.of("房租", "水电燃气", "物业费", "维修保养", "网费话费")),
        new SeedL1("医疗健康", "💊", List.of("看病挂号", "药品购买", "体检检查", "医疗器材")),
        new SeedL1("休闲娱乐", "🎮", List.of("电影演出", "游戏充值", "旅游出行", "运动健身", "宠物开销")),
        new SeedL1("教育学习", "📚", List.of("课程培训", "书籍资料", "文具用品", "考试报名")),
        new SeedL1("人情往来", "🎁", List.of("份子红包", "孝敬长辈", "请客送礼", "慈善捐款")),
        new SeedL1("其他支出", "📦", List.of("快递邮费", "金融服务费", "其他杂项"))
    );

    private static final List<SeedL1> DEFAULT_INCOME = List.of(
        new SeedL1("工资薪酬", "💼", List.of("基本工资", "绩效奖金", "加班补贴", "年终奖金")),
        new SeedL1("副业兼职", "💰", List.of("自由职业", "咨询服务", "稿费版税", "设计制作")),
        new SeedL1("投资理财", "📈", List.of("股票基金收益", "银行利息", "房屋租金", "分红")),
        new SeedL1("红包礼金", "🎁", List.of("节日红包", "生日礼金", "婚礼份子回收", "长辈给的钱")),
        new SeedL1("退款返利", "🔄", List.of("购物退款", "返利提现", "押金退还", "保险理赔")),
        new SeedL1("其他收入", "📦", List.of("二手出售", "报销款", "意外所得", "其他"))
    );
}
