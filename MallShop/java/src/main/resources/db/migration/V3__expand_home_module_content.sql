UPDATE home_modules
SET content_json = '{"items":[{"icon":"¥0.01","name":"一分专享","theme":"mint","type":"penny"},{"icon":"券","name":"新人福利","theme":"orange","type":"new"},{"icon":"净","name":"绿捷净菜","theme":"pink","type":"clean"},{"icon":"砸","name":"欢乐金蛋","theme":"yellow","type":"egg"}]}',
    updated_at = CURRENT_TIMESTAMP
WHERE module_type = 'QUICK_ENTRY'
  AND content_json = '{"items":[{"name":"一分专享","type":"penny"},{"name":"新人福利","type":"new"},{"name":"绿捷净菜","type":"clean"},{"name":"欢乐金蛋","type":"egg"}]}';

UPDATE home_modules
SET content_json = '{"items":[{"kicker":"省心搭配","title":"在家做饭","linkText":"去看看 →","image":"/assets/products/mushroom.jpg","theme":"pink","type":"clean"},{"kicker":"本周爆款","title":"9.9 特惠","linkText":"限时抢 →","image":"/assets/products/orange.jpg","theme":"yellow","type":"deal"}]}',
    updated_at = CURRENT_TIMESTAMP
WHERE module_type = 'ACTIVITY_ZONE'
  AND content_json = '{"items":[{"title":"今晚在家做饭","type":"clean"},{"title":"9.9 特惠专区","type":"deal"},{"title":"鲜切果盒专区","type":"fruit-cup"}]}';

UPDATE home_modules
SET content_json = '{"icon":"闪","primaryText":"城区 3 公里配送","secondaryText":"30 分钟送货上门"}',
    updated_at = CURRENT_TIMESTAMP
WHERE module_type = 'ANNOUNCEMENT'
  AND content_json = '{"text":"城区 3 公里配送，满 30 元免配送费，30 分钟送货上门"}';
